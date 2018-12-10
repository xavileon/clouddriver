package com.netflix.spinnaker.clouddriver.aws.deploy.converters;

import com.netflix.spinnaker.clouddriver.aws.AmazonOperation;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.DeployCloudFormationDescription;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.DeployCloudFormationAtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport;
import org.springframework.stereotype.Component;

import java.util.Map;

@AmazonOperation(AtomicOperations.DEPLOY_CLOUDFORMATION)
@Component("deployCloudFormationDescription")
public class DeployCloudFormationAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {
  @Override
  public AtomicOperation convertOperation(Map input) {
    return new DeployCloudFormationAtomicOperation(convertDescription(input));
  }

  @Override
  public DeployCloudFormationDescription convertDescription(Map input) {
    DeployCloudFormationDescription converted = getObjectMapper()
        .convertValue(input, DeployCloudFormationDescription.class);
    converted.setCredentials(getCredentialsObject((String) input.get("credentials")));
    return converted;
  }
}
