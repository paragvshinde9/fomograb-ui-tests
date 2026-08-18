package com.fomograb.uitests.core;

import org.testng.IAnnotationTransformer;
import org.testng.annotations.ITestAnnotation;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Wires {@link RetryAnalyzer} onto every {@code @Test} method in the suite,
 * so nobody has to remember to write {@code @Test(retryAnalyzer = ...)} by
 * hand on each new test class. Registered once in {@code testng.xml}'s
 * {@code <listeners>}.
 */
public class RetryTransformer implements IAnnotationTransformer {

    @Override
    public void transform(ITestAnnotation annotation, Class testClass, Constructor testConstructor, Method testMethod) {
        annotation.setRetryAnalyzer(RetryAnalyzer.class);
    }
}
