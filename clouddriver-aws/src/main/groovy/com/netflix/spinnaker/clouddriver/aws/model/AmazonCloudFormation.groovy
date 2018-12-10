/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.aws.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.netflix.spinnaker.clouddriver.model.CloudFormation
import groovy.transform.Immutable

@Immutable
@JsonInclude(JsonInclude.Include.NON_EMPTY)
class AmazonCloudFormation implements CloudFormation {
  final String type = "aws"
  final String stackId
  final Map tags
  final Map outputs
  final String stackName
  final String stack
  final String stackStatus
  final String accountName
  final String region
}
