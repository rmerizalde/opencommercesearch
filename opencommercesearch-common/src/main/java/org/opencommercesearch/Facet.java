package org.opencommercesearch;

/*
* Licensed to OpenCommerceSearch under one
* or more contributor license agreements. See the NOTICE file
* distributed with this work for additional information
* regarding copyright ownership. OpenCommerceSearch licenses this
* file to you under the Apache License, Version 2.0 (the
* "License"); you may not use this file except in compliance
* with the License. You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied. See the License for the
* specific language governing permissions and limitations
* under the License.
*/

import java.util.List;
import java.util.Map;

public class Facet {
    private String name;
    private List<Filter> filters;
    private Map<String,String> metadata;
    private Integer minFields;
    
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
    
    public List<Filter> getFilters() {
        return filters;
    }
    
    public void setFilter(List<Filter> filters) {
        this.filters = filters;
    }
        
    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    public Integer getMinFields() {
		return minFields;
	}

	public void setMinFields(Integer minFields) {
		this.minFields = minFields;
	}

	@Override
    public String toString() {
        return "Facet [name=" + name + "]";
    }


    public static class Filter {

        private String name;
        private long count;
        private String path; 
        
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }        
        
        public long getCount() {
            return count;
        }

        public void setCount(long count) {
            this.count = count;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }
        
        @Override
        public String toString() {
            return "Filter [name=" + name + ", count=" + count + ", path="
                    + path + "]";
        }
        
    }
}
