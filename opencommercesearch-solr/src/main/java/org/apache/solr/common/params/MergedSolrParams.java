package org.apache.solr.common.params;

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

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.common.util.IteratorChain;

import java.util.*;

/**
 * SolrParams wrapper which acts similar to AppendedSolrParams except that
 * it allows new values to be set directly into this class. When a value is added,
 * it is appended to all existing values. When a value is set, it overwrites all existing
 * values for that field.
 */
public class MergedSolrParams extends SolrQuery {

    protected final SolrParams defaults;
    private Set<String> removed;
    private Map<String,String[]> values;

    public MergedSolrParams() {
        this(new ModifiableSolrParams());
    }
    public MergedSolrParams(SolrParams defaults) {
        values = new LinkedHashMap<String, String[]>();
        removed = new HashSet<String>();
        this.defaults = defaults;
    }

    /**
     * Replace any existing parameter with the given name.  if val==null remove key from params completely.
     */
    @Override
    public MergedSolrParams set( String name, String ... val ) {
        if (val==null || (val.length==1 && val[0]==null)) {
            values.remove(name);
        } else {
            values.put(name, val);
        }

        return this;
    }

    @Override
    public MergedSolrParams set( String name, int val ) {
        set( name, String.valueOf(val) );
        return this;
    }

    @Override
    public MergedSolrParams set( String name, boolean val ) {
        set( name, String.valueOf(val) );
        return this;
    }

    /**
     * Add the given values to any existing name
     * @param name Key
     * @param val Array of value(s) added to the name. NOTE: If val is null
     *     or a member of val is null, then a corresponding null reference
     *     will be included when a get method is called on the key later.
     *  @return this
     */
    @Override
    public MergedSolrParams add( String name, String ... val ) {
        String[] old = values.put(name, val);
        if( old != null ) {
            if( val == null || val.length < 1 ) {
                String[] both = new String[old.length+1];
                System.arraycopy(old, 0, both, 0, old.length);
                both[old.length] = null;
                values.put(name, both);
            }
            else {
                String[] both = new String[old.length+val.length];
                System.arraycopy(old, 0, both, 0, old.length);
                System.arraycopy(val, 0, both, old.length, val.length);
                values.put(name, both);
            }
        }
        return this;
    }

    @Override
    public void add(SolrParams params)
    {
        Iterator<String> names = params.getParameterNamesIterator();
        while (names.hasNext()) {
            String name = names.next();
            set(name, params.getParams(name));
        }
    }

    /**
     * remove a field at the given name
     */
    @Override
    public String[] remove( String name )
    {
        return values.remove( name );
    }

    /** clear all parameters */
    @Override
    public void clear()
    {
        values.clear();
    }

    /**
     * remove the given value for the given name
     *
     * @return true if the item was removed, false if null or not present
     */
    @Override
    public boolean remove(String name, String value) {
        String[] tmp = values.get(name);
        if (tmp==null) return false;
        for (int i=0; i<tmp.length; i++) {
            if (tmp[i].equals(value)) {
                String[] tmp2 = new String[tmp.length-1];
                if (tmp2.length==0) {
                    remove(name);
                } else {
                    System.arraycopy(tmp, 0, tmp2, 0, i);
                    System.arraycopy(tmp, i+1, tmp2, i, tmp.length-i-1);
                    set(name, tmp2);
                }

                removed.add(name);
                return true;
            }
        }
        return false;
    }

    @Override
    public String get(String param) {
        String[] val = values.get(param);
        if((val != null && val.length > 0) || removed.contains(param)) {
            if(val == null) {
                return null;
            }
            else {
                return val[0];
            }
        }
        else {
            return defaults.get(param);
        }
    }

    @Override
    public String[] getParams(String param) {
        String[] main = values.get(param);
        String[] extra = defaults.getParams(param);
        if (null == extra || 0 == extra.length) {
            return main;
        }
        if (null == main || 0 == main.length) {
            return extra;
        }
        String[] result = new String[main.length + extra.length];
        System.arraycopy(main,0,result,0,main.length);
        System.arraycopy(extra,0,result,main.length,extra.length);
        return result;
    }

    @Override
    public Iterator<String> getParameterNamesIterator() {
        final IteratorChain<String> c = new IteratorChain<String>();
        c.addIterator(defaults.getParameterNamesIterator());
        c.addIterator(values.keySet().iterator());
        return c;
    }

    @Override
    public String toString() {
        return "params {"+toString(values)+"}";
    }

    /**
     * Helper method that prints a values map to string.
     * @param vals Values map.
     * @return String representing the current values map.
     */
    private String toString(Map<String, String[]> vals) {
        StringBuilder sb = new StringBuilder(128);
        boolean first=true;

        for (Map.Entry<String,String[]> entry : vals.entrySet()) {
            String key = entry.getKey();
            String[] valarr = entry.getValue();
            for (String val : valarr) {
                if (!first) sb.append('&');
                first=false;
                sb.append(key);
                sb.append('=');
                if( val != null ) {
                    sb.append(val);
                }
            }
        }

        return sb.toString();
    }
}
