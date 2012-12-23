package org.opencommercesearch.junit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p>The <code>SearchTest</code> annotation tells JUnit that the <code>public void</code> method
 * to which it is attached can be run as a test case. To run the method,
 * JUnit first constructs a fresh instance of the class then invokes the
 * annotated method. Any exceptions thrown by the test will be reported
 * by JUnit as a failure. If no exceptions are thrown, the test is assumed
 * to have succeeded.</p>
 *
 * <p>For more details see {@code @Test}</p>
 * 
 * @rmerizalde
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface SearchTest {
    boolean newInstance() default false;
    String productData() default "";
    String rulesData() default "";
}
