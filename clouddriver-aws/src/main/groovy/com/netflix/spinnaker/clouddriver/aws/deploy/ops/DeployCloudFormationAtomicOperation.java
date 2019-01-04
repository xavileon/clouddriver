/*
 * Copyright (c) 2018 Schibsted Media Group. All rights reserved
 */

package com.netflix.spinnaker.clouddriver.aws.deploy.ops;

import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.cloudformation.model.CreateStackRequest;
import com.amazonaws.services.cloudformation.model.CreateStackResult;
import com.amazonaws.services.cloudformation.model.DescribeStackEventsRequest;
import com.amazonaws.services.cloudformation.model.DescribeStackInstanceRequest;
import com.amazonaws.services.cloudformation.model.DescribeStackInstanceResult;
import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.StackEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.aws.data.Keys;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.DeployCloudFormationDescription;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class DeployCloudFormationAtomicOperation implements AtomicOperation<Map> {

  private static final String BASE_PHASE = "DEPLOY_CLOUDFORMATION";


  @Autowired
  private AmazonClientProvider amazonClientProvider;

  @Autowired
  private ObjectMapper objectMapper;

  private DeployCloudFormationDescription description;

  public DeployCloudFormationAtomicOperation(DeployCloudFormationDescription deployCloudFormationDescription) {
    this.description = deployCloudFormationDescription;
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  @Override
  public Map operate(List priorOutputs) {
    getTask().updateStatus(BASE_PHASE, "Configurting cloudformation");
    AmazonCloudFormation amazonCloudFormation = amazonClientProvider.getAmazonCloudFormation(
        description.getCredentials(), description.getRegion());
    getTask().updateStatus(BASE_PHASE, "Preparing cloudformation");
    CreateStackRequest createStackRequest = new CreateStackRequest()
        .withStackName(description.getStackName())
        .withParameters(description.getParameters().entrySet().stream()
            .map(entry -> new Parameter()
                .withParameterKey(entry.getKey())
                .withParameterValue(entry.getValue()))
            .collect(Collectors.toList()));
    try {
      getTask().updateStatus(BASE_PHASE, "Generating cloudformation");
      createStackRequest = createStackRequest.withTemplateBody(
          objectMapper.writeValueAsString(description.getTemplateBody()));
    } catch (JsonProcessingException e) {
      log.warn("Could not serialize templateBody: {}", description, e);
    }
    getTask().updateStatus(BASE_PHASE, "Uploading cloudformation");
    CreateStackResult createStackResult = amazonCloudFormation.createStack(createStackRequest);
    waitForCloudFormationCompletion(amazonCloudFormation, createStackResult.getStackId(), description.getStackName());
    checkCloudFormationStatus(amazonCloudFormation, description.getStackName());
    getTask().addResultObjects(Collections.singletonList(Collections.singletonMap("GARD","TESTING")));
    String cacheKey = Keys.getCloudFormationKey(createStackResult.getStackId(), description.getAccount(), description.getRegion());
    //mark cache as stale // force cache refresh
    return Collections.singletonMap("cacheId", cacheKey);
  }

  private void waitForCloudFormationCompletion(AmazonCloudFormation amazonCloudFormation, String stackId, String stackName) {
    boolean finished = false;
    getTask().updateStatus(BASE_PHASE, "Wait for cloudformation to stabilize");

    DescribeStackEventsRequest describeStackEventsRequest = new DescribeStackEventsRequest().withStackName(stackName);
    while (!finished) {
      List<StackEvent> stackEvents = amazonCloudFormation.describeStackEvents(describeStackEventsRequest).getStackEvents();
      finished = stackEvents.stream()
        .peek(event -> log.info("event type {} status {}", event.getResourceType(), event.getResourceStatus()))
        .filter(event -> event.getResourceType().equals("AWS::CloudFormation::Stack"))
        .filter(event -> event.getResourceStatus().equals("CREATE_COMPLETE") || event.getResourceStatus().equals("ROLLBACK_COMPLETE"))
        .findAny().isPresent();
      if (!finished) {
        try {
          Thread.sleep(2000L);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    }
  }

  private boolean checkCloudFormationStatus(AmazonCloudFormation amazonCloudFormation, String stackName) {
    getTask().updateStatus(BASE_PHASE, "Check status of cloudformation exection");
    DescribeStackEventsRequest describeStackEventsRequest = new DescribeStackEventsRequest().withStackName(stackName);
    List<StackEvent> stackEvents = amazonCloudFormation.describeStackEvents(describeStackEventsRequest).getStackEvents();
    Optional<StackEvent> stackEvent = stackEvents.stream()
      .filter(event -> event.getResourceType().equals("AWS::CloudFormation::Stack"))
      .filter(event -> event.getResourceStatus().equals("ROLLBACK_COMPLETE"))
      .findAny();

    if(stackEvent.isPresent()) {
      if (stackEvent.get().getResourceStatus().equals("ROLLBACK_COMPLETE")) {
        stackEvents.stream()
          .filter(event -> event.getResourceStatus().equals("CREATE_FAILED"))
          .peek(event -> {
            log.info("Issue: {}", event.getResourceStatusReason());
            getTask().updateStatus("APPLY_CLOUDFORMATION", event.toString());// event.getResourceStatusReason());
            }
          ).findAny().isPresent();
        getTask().fail();
        return false;
      }
    }
    return true;
  }
}
