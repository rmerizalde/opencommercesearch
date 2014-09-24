package org.opencommercesearch.junit.runners.statements;

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

import org.opencommercesearch.SearchServer;
import org.opencommercesearch.SearchServerManager;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

/**
 * A simple JUnit statement to execute a search test.
 * @rmerizalde
 */
public class SearchInvokeMethod extends Statement {
    private final FrameworkMethod fTestMethod;
	private Object fTarget;
    private SearchServer fSearchServer;

	public SearchInvokeMethod(FrameworkMethod testMethod, Object target, SearchServer searchServer) {
		fTestMethod= testMethod;
		fTarget= target;
        fSearchServer = searchServer;
	}

	@Override
	public void evaluate() throws Throwable {
		fTestMethod.invokeExplosively(fTarget, fSearchServer);

        SearchServerManager.getInstance().shutdown(fSearchServer);
	}
}
