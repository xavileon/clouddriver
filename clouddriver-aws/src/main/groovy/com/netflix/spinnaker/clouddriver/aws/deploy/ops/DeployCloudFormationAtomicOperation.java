/*
 * Copyright (c) 2018 Schibsted Media Group. All rights reserved
 */

package com.netflix.spinnaker.clouddriver.aws.deploy.ops;

import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.cloudformation.model.CreateStackRequest;
import com.amazonaws.services.cloudformation.model.CreateStackResult;
import com.amazonaws.services.cloudformation.model.Parameter;
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
    long waitTime = 10000L;
    try {
      Thread.sleep(waitTime);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    AmazonCloudFormation amazonCloudFormation = amazonClientProvider.getAmazonCloudFormation(
        description.getCredentials(), description.getRegion());
    getTask().updateStatus(BASE_PHASE, "Preparing cloudformation");
    try {
      Thread.sleep(waitTime);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    CreateStackRequest createStackRequest = new CreateStackRequest()
        .withStackName(description.getStackName())
        .withParameters(description.getParameters().entrySet().stream()
            .map(entry -> new Parameter()
                .withParameterKey(entry.getKey())
                .withParameterValue(entry.getValue()))
            .collect(Collectors.toList()));
    try {
      getTask().updateStatus(BASE_PHASE, "Uploading cloudformation");
      try {
        Thread.sleep(waitTime);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      createStackRequest = createStackRequest.withTemplateBody(
          objectMapper.writeValueAsString(description.getTemplateBody()));
    } catch (JsonProcessingException e) {
      log.warn("Could not serialize templateBody: {}", description, e);
    }
    getTask().updateStatus(BASE_PHASE, "Finished uploading cloudformation");
    try {
      Thread.sleep(waitTime);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    CreateStackResult createStackResult = amazonCloudFormation.createStack(createStackRequest);
    log.info("CLOUDFORMATION GETSTACKID {}", createStackResult.getStackId());
    log.info("CLOUDFORMATION TASKID {}", getTask().getId());
    getTask().addResultObjects(Collections.singletonList(Collections.singletonMap("GARD","TESTING")));
    String cacheKey = Keys.getCloudFormationKey(createStackResult.getStackId(), description.getAccount(), description.getRegion());
    //mark cache as stale // force cache refresh
    return Collections.singletonMap("cacheId", cacheKey);
  }
}
