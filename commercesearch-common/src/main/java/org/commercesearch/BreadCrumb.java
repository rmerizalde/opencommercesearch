package org.commercesearch;


public class BreadCrumb {
    private String fieldName;
    private String expression;
    private String path;

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getExpression() {
        return expression;
    }

    public void setExpression(String expression) {
        this.expression = Utils.getRangeName(getFieldName(), expression);
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }


    @Override
    public String toString() {
        return fieldName + ":" + expression + "->" + path;
    }
}
