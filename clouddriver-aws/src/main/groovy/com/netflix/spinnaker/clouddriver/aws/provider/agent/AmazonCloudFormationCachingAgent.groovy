/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.aws.provider.agent

import com.amazonaws.services.cloudformation.AmazonCloudFormation
import com.amazonaws.services.cloudformation.model.Stack
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.agent.*
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.aws.cache.Keys
import com.netflix.spinnaker.clouddriver.aws.provider.AwsInfrastructureProvider
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import groovy.util.logging.Slf4j

import static com.netflix.spinnaker.clouddriver.aws.cache.Keys.Namespace.CLOUDFORMATION_STACKS
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE

@Slf4j
class AmazonCloudFormationCachingAgent implements CachingAgent, AccountAware {
  final AmazonClientProvider amazonClientProvider
  final NetflixAmazonCredentials account
  final String region
  final ObjectMapper objectMapper

  static final Set<AgentDataType> types = Collections.unmodifiableSet([
    AUTHORITATIVE.forType(CLOUDFORMATION_STACKS.ns)
  ] as Set)

  AmazonCloudFormationCachingAgent(AmazonClientProvider amazonClientProvider,
                                   NetflixAmazonCredentials account,
                                   String region,
                                   ObjectMapper objectMapper
                                   ) {
    this.amazonClientProvider = amazonClientProvider
    this.account = account
    this.region = region
    this.objectMapper = objectMapper
  }

  @Override
  String getProviderName() {
    AwsInfrastructureProvider.PROVIDER_NAME
  }

  @Override
  String getAgentType() {
    "${account.name}/${region}/${AmazonCloudFormationCachingAgent.simpleName}"
  }

  @Override
  String getAccountName() {
    account.name
  }

  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    return types
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    log.info("Describing items in ${agentType}")
    AmazonCloudFormation cloudformation = amazonClientProvider.getAmazonCloudFormation(account, region)

    List<Stack> stacks = cloudformation.describeStacks().stacks

    Collection<CacheData> stackCacheData = new ArrayList<>(stacks.size())

    for (Stack stack : stacks) {
      def stackId = Keys.getCloudFormationStackKey(stack.stackId, region, account.name)
      stackCacheData.add(new DefaultCacheData(stackId, [
        stackId: stack.stackId,
        tags: stack.tags,
        outputs: stack.outputs,
        stackName: stack.stackName,
        accountName: account.name,
        stackStatus: stack.stackStatus,
        creationTime: stack.creationTime
      ], [(CLOUDFORMATION_STACKS.ns): [stackId]]))
    }

    log.info("Caching ${stackCacheData.size()} items in ${agentType}")
    stackCacheData.each {
      log.debug("Cached stack ${it.properties}")
    }
    new DefaultCacheResult([(CLOUDFORMATION_STACKS.ns): stackCacheData])
  }
}
