/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.single.yaml.config.swapper;

import org.apache.shardingsphere.single.api.config.SingleRuleConfiguration;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

// TODO Rename YamlSingleRuleConfigurationSwapperTest when metadata structure adjustment completed. #25485
class NewYamlSingleRuleConfigurationSwapperTest {
    
    private final NewYamlSingleRuleConfigurationSwapper swapper = new NewYamlSingleRuleConfigurationSwapper();
    
    @Test
    void assertSwapToDataNodes() {
        assertThat(swapper.swapToDataNodes(new SingleRuleConfiguration()).iterator().next().getKey(), is("/rules/single"));
    }
}
