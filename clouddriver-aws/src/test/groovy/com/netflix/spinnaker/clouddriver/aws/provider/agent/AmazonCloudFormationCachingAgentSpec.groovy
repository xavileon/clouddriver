/*
 * Copyright (c) 2019 Schibsted Media Group.
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
import com.amazonaws.services.cloudformation.model.DescribeStackEventsResult
import com.amazonaws.services.cloudformation.model.DescribeStacksResult
import com.amazonaws.services.cloudformation.model.Stack
import com.amazonaws.services.cloudformation.model.StackEvent
import com.amazonaws.services.ec2.AmazonEC2
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.aws.cache.Keys
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class AmazonCloudFormationCachingAgentSpec extends Specification {
  static String region = 'region'
  static String account = 'account'

  @Subject
  AmazonCloudFormationCachingAgent agent

  @Shared
  ProviderCache providerCache = Mock(ProviderCache)

  @Shared
  AmazonEC2 ec2

  @Shared
  AmazonClientProvider acp

  def setup() {
    ec2 = Mock(AmazonEC2)
    def creds = Stub(NetflixAmazonCredentials) {
      getAccountId() >> account
    }
    acp = Mock(AmazonClientProvider)
    agent = new AmazonCloudFormationCachingAgent(acp, creds, region)
  }

  void "should add cloud formations on initial run"() {
    given:
    def amazonCloudFormation = Mock(AmazonCloudFormation)
    def stackResults = Mock(DescribeStacksResult)
    def stack1 = new Stack().withStackId("stack1").withStackStatus("CREATE_SUCCESS")
    def stack2 = new Stack().withStackId("stack2").withStackStatus("CREATE_SUCCESS")

    when:
    def cache = agent.loadData(providerCache)
    def results = cache.cacheResults[Keys.Namespace.CLOUDFORMATION.ns]

    then:
    1 * acp.getAmazonCloudFormation(_, _) >> amazonCloudFormation
    1 * amazonCloudFormation.describeStacks() >> stackResults
    1 * stackResults.stacks >> [ stack1, stack2 ]

    results.find { it.id == Keys.getCloudFormationKey("stack1", "region", "account") }.attributes.'stackId' == stack1.stackId
    results.find { it.id == Keys.getCloudFormationKey("stack2", "region", "account") }.attributes.'stackId' == stack2.stackId
  }

  void "should evict cloudformations when not found on subsequent runs"() {
    given:
    def amazonCloudFormation = Mock(AmazonCloudFormation)
    def stackResults = Mock(DescribeStacksResult)
    def stack1 = new Stack().withStackId("stack1").withStackStatus("CREATE_SUCCESS")
    def stack2 = new Stack().withStackId("stack2").withStackStatus("CREATE_SUCCESS")

    when:
    def cache = agent.loadData(providerCache)
    def results = cache.cacheResults[Keys.Namespace.CLOUDFORMATION.ns]

    then:
    1 * acp.getAmazonCloudFormation(_, _) >> amazonCloudFormation
    1 * amazonCloudFormation.describeStacks() >> stackResults
    1 * stackResults.stacks >> [ stack1, stack2 ]

    results.find { it.id == Keys.getCloudFormationKey("stack1", "region", "account") }.attributes.'stackId' == stack1.stackId
    results.find { it.id == Keys.getCloudFormationKey("stack2", "region", "account") }.attributes.'stackId' == stack2.stackId

    when:
    cache = agent.loadData(providerCache)
    results = cache.cacheResults[Keys.Namespace.CLOUDFORMATION.ns]

    then:
    1 * acp.getAmazonCloudFormation(_, _) >> amazonCloudFormation
    1 * amazonCloudFormation.describeStacks() >> stackResults
    1 * stackResults.stacks >> [ stack1 ]

    results.find { it.id == Keys.getCloudFormationKey("stack1", "region", "account") }.attributes.'stackId' == stack1.stackId
    results.find { it.id == Keys.getCloudFormationKey("stack2", "region", "account") } == null
  }

  void "should include stack status reason when state is ROLLBACK_COMPLETE (failed)"() {
    given:
    def amazonCloudFormation = Mock(AmazonCloudFormation)
    def stack = new Stack().withStackId("stack1").withStackStatus("ROLLBACK_COMPLETE")
    def stackResults = Mock(DescribeStacksResult)
    def stackEvent = new StackEvent().withResourceStatus("CREATE_FAILED").withResourceStatusReason("who knows")
    def stackEventResults = Mock(DescribeStackEventsResult)

    when:
    def cache = agent.loadData(providerCache)
    def results = cache.cacheResults[Keys.Namespace.CLOUDFORMATION.ns]

    then:
    1 * acp.getAmazonCloudFormation(_, _) >> amazonCloudFormation
    1 * amazonCloudFormation.describeStacks() >> stackResults
    1 * stackResults.stacks >> [ stack ]
    1 * amazonCloudFormation.describeStackEvents(_) >> stackEventResults
    1 * stackEventResults.getStackEvents() >> [ stackEvent ]

    results.find { it.id == Keys.getCloudFormationKey("stack1", "region", "account") }.attributes.'stackStatusReason' == 'who knows'
  }
}
