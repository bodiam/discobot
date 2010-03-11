/*
 * Copyright 2003-2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.codehaus.groovy.tools.gse;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

public class StringSetMap extends HashMap<String,Set<String>> {
    
    public StringSetMap() {
        super();
    }
    
    public StringSetMap(StringSetMap other) {
        for (String key : other.keySet()) {
            get(key).addAll(other.get(key));
        }
    }
    
    public Set<String> get(Object o){
        String name = (String) o;
        Set<String> set = super.get(name);
        if (set==null) {
            set = new HashSet();
            put(name,set);
        }
        return set;
    }

    public void makeTransitiveHull() {
        TreeSet<String> nameSet = new TreeSet(keySet());
        StringSetMap ret = new StringSetMap(this);
        
        for (String k: nameSet) {
            StringSetMap delta = new StringSetMap();
            for (String i: nameSet) {
                for (String j: nameSet) {
                    Set<String> iSet = get(i);
                    if (iSet.contains(k) && get(k).contains(j)) {
                        delta.get(i).add(j);
                    }
                }
            }
            for (String i: nameSet) get(i).addAll(delta.get(i));
        }
    }
}
