package org.opencommercesearch;

import java.util.List;


public class CategoryGraph implements Comparable<CategoryGraph> {

    private List<CategoryGraph> categoryGraphNodes;
    private int count;
    private String path;
    private String id;
    
    public List<CategoryGraph> getCategoryGraphNodes() {
        return categoryGraphNodes;
    }
    public void setCategoryGraphNodes(List<CategoryGraph> categoryGraphNodes) {
        this.categoryGraphNodes = categoryGraphNodes;
    }
    public int getCount() {
        return count;
    }
    public void setCount(int count) {
        this.count = count;
    }
    public String getPath() {
        return path;
    }
    public void setPath(String path) {
        this.path = path;
    }
    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }
    @Override
    public int compareTo(CategoryGraph o) {
        return id.compareTo(o.getId());
    }
    @Override
    public String toString() {
        return "CategoryGraph [count=" + count + ", path=" + path + ", id="
                + id + "]";
    }

}
