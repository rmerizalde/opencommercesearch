package org.commercesearch.junit.runners.statements;

import org.commercesearch.SearchServer;
import org.commercesearch.SearchServerManager;
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
