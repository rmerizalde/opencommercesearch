package org.commercesearch;

import java.util.List;

public class Facet {
    private String name;
    private List<Filter> filters;

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
        
    }
}
