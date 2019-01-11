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

package com.netflix.spinnaker.clouddriver.aws.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.aws.cache.Keys
import com.netflix.spinnaker.clouddriver.aws.model.AmazonCloudFormation
import com.netflix.spinnaker.clouddriver.model.CloudFormationProvider
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.clouddriver.aws.cache.Keys.Namespace.CLOUDFORMATION_STACKS

@Slf4j
@Component
class AmazonCloudFormationProvider implements CloudFormationProvider<AmazonCloudFormation> {

  private final Cache cacheView
  private final ObjectMapper objectMapper

  @Autowired
  AmazonCloudFormationProvider(Cache cacheView, ObjectMapper objectMapper) {
    this.cacheView = cacheView
    this.objectMapper = objectMapper
  }

  @Override
  Set<AmazonCloudFormation> getAllByAccount(String account) {
    def filter = Keys.getCloudFormationStackKey('*', '*', account)
    log.debug("Get all stacks with filter $filter")
    loadResults(cacheView.filterIdentifiers(CLOUDFORMATION_STACKS.ns, filter))
  }

  @Override
  Set<AmazonCloudFormation> getAllByAccountAndRegion(String account, String region) {
    def filter = Keys.getCloudFormationStackKey('*', region, account)
    log.debug("Get all stacks with filter $filter")
    loadResults(cacheView.filterIdentifiers(CLOUDFORMATION_STACKS.ns, filter))
  }

  @Override
  AmazonCloudFormation getByStackId(String stackId) {
    def filter = Keys.getCloudFormationStackKey(stackId, '*', '*')
    log.debug("Get all stacks with filter $filter")
    loadResults(cacheView.filterIdentifiers(CLOUDFORMATION_STACKS.ns, filter)).first()
  }

  Set<AmazonCloudFormation> loadResults(Collection<String> identifiers) {
    log.debug("Cloud formation requested identifies: $identifiers")
    cacheView.getAll(CLOUDFORMATION_STACKS.ns, identifiers, RelationshipCacheFilter.none()).collect { CacheData data ->
      log.debug("Cloud formation cached properties ${data.properties}")
      objectMapper.convertValue(data.attributes, AmazonCloudFormation)
    }
  }
}
