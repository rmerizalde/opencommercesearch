package org.commercesearch;

import org.apache.commons.lang.StringUtils;

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
        if (expression.startsWith("[") && expression.endsWith("]")) {
            String[] parts = StringUtils.split(expression.substring(1, expression.length() - 1), " TO ");
            if (parts.length == 2) {
                String key = Utils.RESOURCE_IN_RANGE;
                if ("*".equals(parts[0])) {
                    key = Utils.RESOURCE_BEFORE;
                } else if ("*".equals(parts[1])) {
                    key = Utils.RESOURCE_AFTER;
                }
                this.expression = Utils.getRangeName(getFieldName(), key, parts[0], parts[1]);
                return;
            }
        }
        this.expression = expression;
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
