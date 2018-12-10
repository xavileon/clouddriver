/*
 * Copyright (c) 2018 Schibsted Media Group. All rights reserved
 */

package com.netflix.spinnaker.clouddriver.aws.deploy.description;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.HashMap;
import java.util.Map;

@EqualsAndHashCode(callSuper = false)
@Data
public class DeployCloudFormationDescription extends AbstractAmazonCredentialsDescription {

  private String stackName;
  private Map<String, Object> templateBody = new HashMap<>();
  private Map<String, String> parameters = new HashMap<>();
  private String region;
}
