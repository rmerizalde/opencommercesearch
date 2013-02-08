package org.opencommercesearch.inventory;

import atg.adapter.gsa.GSARepository;
import atg.commerce.inventory.InventoryException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockito.Mockito.mock;
import static org.junit.Assert.*;

/**
 * @author rmerizalde
 **/
public class SequentialInMemoryInventoryManagerTest {

    @Mock
    private GSARepository inventoryRepository;

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement stmt;

    @Mock
    private ResultSet rs;

    @Spy
    SequentialInMemoryInventoryManager manager = new SequentialInMemoryInventoryManager();

    @Before
    public void setUp() throws Exception {
        initMocks(this);
        manager.setInventoryRepository(inventoryRepository);
        when(inventoryRepository.getDataSource()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.execute()).thenReturn(true);
        when(stmt.getResultSet()).thenReturn(rs);

        final AtomicInteger index = new AtomicInteger();
        final StringBuffer id = new StringBuffer();
        final HashMap map = new HashMap();
        map.put("SKU0001-01", new String[] {"SKU0001-01", "SKU0001-02", "SKU0002-01", "SKU0002-02"});
        map.put("SKU0002-03", new String[] {"SKU0002-03", "SKU0003-03", "SKU0003-04", "SKU0004-01"});
        map.put("SKU0004-02", new String[] {"SKU0004-02", "SKU0004-03", "SKU0004-04", "SKU0004-05"});


        doAnswer(new Answer() {
            public Object answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                String skuId = (String) args[1];
                id.setLength(0);
                id.append(skuId);
                index.set(-1);
                return null;
            }
        }).when(stmt).setString(eq(1), anyString());

        when(rs.next()).then(new Answer<Boolean>() {
            public Boolean answer(InvocationOnMock invocation) throws Throwable {
                String[] ids = (String[]) map.get(id.toString());

                if (ids != null) {
                    return index.incrementAndGet() < ids.length;
                }

                return false;
            }
        });

        when(rs.getString("catalog_ref_id")).then(new Answer<String>() {
            public String answer(InvocationOnMock invocation) throws Throwable {
                String[] ids = (String[]) map.get(id.toString());
                return ids[index.get()];
            }
        });

        when(rs.getLong("stock_level")).then(new Answer<Long>() {
            public Long answer(InvocationOnMock invocation) throws Throwable {

                String[] ids = (String[]) map.get(id.toString());
                String next = ids[index.get()];
                return Long.parseLong(next.substring(next.length() - 1, next.length()));
            }
        });

    }


    @Test
    public void testQueryStockLevel() throws Exception {
        assertEquals(1, manager.queryStockLevel("SKU0001-01"));
        assertEquals(2, manager.queryStockLevel("SKU0001-02"));
        assertEquals(1, manager.queryStockLevel("SKU0002-01"));
        assertEquals(2, manager.queryStockLevel("SKU0002-02"));
        assertEquals(3, manager.queryStockLevel("SKU0002-03"));
        assertEquals(3, manager.queryStockLevel("SKU0003-03"));
        assertEquals(4, manager.queryStockLevel("SKU0003-04"));
        assertEquals(1, manager.queryStockLevel("SKU0004-01"));
        assertEquals(2, manager.queryStockLevel("SKU0004-02"));
        assertEquals(3, manager.queryStockLevel("SKU0004-03"));
        assertEquals(4, manager.queryStockLevel("SKU0004-04"));
        assertEquals(5, manager.queryStockLevel("SKU0004-05"));
        verify(stmt, times(3)).execute();
    }

    @Test(expected = InventoryException.class)
    public void testQueryStockLevelNotFound() throws Exception {

        assertEquals(5, manager.queryStockLevel("SKU0006-05"));
        verify(stmt, times(1)).execute();
    }

    @Test
    public void testQueryStockLevelBackAndForth() throws Exception {
        assertEquals(1, manager.queryStockLevel("SKU0001-01"));
        assertEquals(2, manager.queryStockLevel("SKU0001-02"));
        assertEquals(1, manager.queryStockLevel("SKU0002-01"));
        assertEquals(2, manager.queryStockLevel("SKU0002-02"));
        assertEquals(3, manager.queryStockLevel("SKU0002-03"));
        assertEquals(3, manager.queryStockLevel("SKU0003-03"));
        assertEquals(4, manager.queryStockLevel("SKU0003-04"));
        assertEquals(1, manager.queryStockLevel("SKU0004-01"));
        assertEquals(2, manager.queryStockLevel("SKU0004-02"));
        assertEquals(3, manager.queryStockLevel("SKU0004-03"));
        assertEquals(4, manager.queryStockLevel("SKU0004-04"));
        assertEquals(5, manager.queryStockLevel("SKU0004-05"));
        assertEquals(1, manager.queryStockLevel("SKU0001-01"));
        assertEquals(2, manager.queryStockLevel("SKU0004-02"));
        assertEquals(3, manager.queryStockLevel("SKU0002-03"));


        verify(stmt, times(6)).execute();
    }

}
