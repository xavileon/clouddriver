/*
 * Copyright (c) 2018 Schibsted Media Group. All rights reserved
 */

package com.netflix.spinnaker.clouddriver.aws.deploy.ops;

import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.cloudformation.model.CreateStackRequest;
import com.amazonaws.services.cloudformation.model.CreateStackResult;
import com.amazonaws.services.cloudformation.model.Parameter;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.DeployCloudFormationDescription;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.stream.Collectors;

public class DeployCloudFormationAtomicOperation implements AtomicOperation<String> {

  @Autowired
  private AmazonClientProvider amazonClientProvider;

  private DeployCloudFormationDescription description;

  public DeployCloudFormationAtomicOperation(DeployCloudFormationDescription deployCloudFormationDescription) {
    this.description = deployCloudFormationDescription;
  }

  @Override
  public String operate(List priorOutputs) {
    AmazonCloudFormation amazonCloudFormation = amazonClientProvider.getAmazonCloudFormation(
        description.getCredentials(), description.getRegion());
    CreateStackRequest createStackRequest = new CreateStackRequest()
        .withStackName(description.getStackName())
        .withParameters(description.getParameters().entrySet().stream()
          .map(entry -> new Parameter()
              .withParameterKey(entry.getKey())
              .withParameterValue(entry.getValue()))
            .collect(Collectors.toList())
        ).withTemplateBody(description.getTemplateBody());

    CreateStackResult createStackResult = amazonCloudFormation.createStack(createStackRequest);

    return createStackResult.getStackId();
  }
}
