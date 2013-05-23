package org.opencommercesearch;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import java.util.List;

import org.junit.Before;
import org.mockito.Mock;

public class EvaluationServiceUnitTest {
	
    EvaluationService evaluationService = new EvaluationService();
	
    @Mock
    SearchServer evaluationSearchServer;
	
    @Mock
    String queryFileName;
	
    @Mock
    String	searchOutputFileName;
	
    @Mock
    List<String> siteIds;
	
	@Before
    public void setUp() throws Exception {
        initMocks(this);
        
    }
}
