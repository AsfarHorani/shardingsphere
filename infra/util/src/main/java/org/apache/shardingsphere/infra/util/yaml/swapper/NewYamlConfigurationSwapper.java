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

package org.apache.shardingsphere.infra.util.yaml.swapper;

import org.apache.shardingsphere.infra.util.yaml.datanode.YamlDataNode;

import java.util.Collection;

/**
 * TODO Rename YamlConfigurationSwapper when metadata structure adjustment completed. #25485
 * YAML configuration swapper.
 *
 * @param <T> type of swapped object
 */
public interface NewYamlConfigurationSwapper<T> {
    
    /**
    * Swap to YAML data node.
    *
    * @param data data to be swapped
    * @return YAML data nodes
    */
    Collection<YamlDataNode> swapToDataNodes(T data);
    
    /**
     * Swap from data node to object.
     *
     * @param dataNodes data nodes
     * @return swapped object
     */
    T swapToObject(Collection<YamlDataNode> dataNodes);
}
