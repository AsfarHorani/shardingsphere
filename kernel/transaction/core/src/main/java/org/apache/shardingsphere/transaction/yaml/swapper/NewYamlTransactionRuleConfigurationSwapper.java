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

package org.apache.shardingsphere.transaction.yaml.swapper;

import org.apache.shardingsphere.infra.converter.GlobalRuleNodeConverter;
import org.apache.shardingsphere.infra.util.yaml.YamlEngine;
import org.apache.shardingsphere.infra.util.yaml.datanode.YamlDataNode;
import org.apache.shardingsphere.infra.yaml.config.swapper.rule.NewYamlRuleConfigurationSwapper;
import org.apache.shardingsphere.transaction.config.TransactionRuleConfiguration;
import org.apache.shardingsphere.transaction.constant.TransactionOrder;
import org.apache.shardingsphere.transaction.yaml.config.YamlTransactionRuleConfiguration;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.Properties;

/**
 * TODO Rename YamlTransactionRuleConfigurationSwapper when metadata structure adjustment completed. #25485
 * New YAML Transaction rule configuration swapper.
 */
public final class NewYamlTransactionRuleConfigurationSwapper implements NewYamlRuleConfigurationSwapper<TransactionRuleConfiguration> {
    
    @Override
    public Collection<YamlDataNode> swapToDataNodes(final TransactionRuleConfiguration data) {
        return Collections.singletonList(new YamlDataNode(GlobalRuleNodeConverter.getRootNode(getRuleTagName().toLowerCase()), YamlEngine.marshal(swapToYamlConfiguration(data))));
    }
    
    private YamlTransactionRuleConfiguration swapToYamlConfiguration(final TransactionRuleConfiguration data) {
        YamlTransactionRuleConfiguration result = new YamlTransactionRuleConfiguration();
        result.setDefaultType(data.getDefaultType());
        result.setProviderType(data.getProviderType());
        result.setProps(data.getProps());
        return result;
    }
    
    @Override
    public TransactionRuleConfiguration swapToObject(final Collection<YamlDataNode> dataNodes) {
        for (YamlDataNode each : dataNodes) {
            Optional<String> version = GlobalRuleNodeConverter.getVersion(getRuleTagName().toLowerCase(), each.getKey());
            if (!version.isPresent()) {
                continue;
            }
            return YamlEngine.unmarshal(each.getValue(), TransactionRuleConfiguration.class);
        }
        return new TransactionRuleConfiguration("", "", new Properties());
    }
    
    @Override
    public Class<TransactionRuleConfiguration> getTypeClass() {
        return TransactionRuleConfiguration.class;
    }
    
    @Override
    public String getRuleTagName() {
        return "TRANSACTION";
    }
    
    @Override
    public int getOrder() {
        return TransactionOrder.ORDER;
    }
}
