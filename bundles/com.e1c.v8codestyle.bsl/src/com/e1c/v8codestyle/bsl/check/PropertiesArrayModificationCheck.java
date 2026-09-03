/*******************************************************************************
 * Copyright (C) 2026, 1C-Soft LLC and others.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     1C-Soft LLC - initial API and implementation
 *******************************************************************************/
package com.e1c.v8codestyle.bsl.check;

import static com._1c.g5.v8.dt.bsl.model.BslPackage.Literals.METHOD;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.xtext.EcoreUtil2;

import com._1c.g5.v8.dt.bsl.model.DynamicFeatureAccess;
import com._1c.g5.v8.dt.bsl.model.Function;
import com._1c.g5.v8.dt.bsl.model.Invocation;
import com._1c.g5.v8.dt.bsl.model.Method;
import com._1c.g5.v8.dt.bsl.model.Module;
import com._1c.g5.v8.dt.bsl.model.ReturnStatement;
import com._1c.g5.v8.dt.bsl.model.SimpleStatement;
import com._1c.g5.v8.dt.bsl.model.Statement;
import com._1c.g5.v8.dt.bsl.model.StaticFeatureAccess;
import com.e1c.g5.v8.dt.check.CheckComplexity;
import com.e1c.g5.v8.dt.check.ICheckParameters;
import com.e1c.g5.v8.dt.check.components.BasicCheck;
import com.e1c.g5.v8.dt.check.components.ModuleTopObjectNameFilterExtension;
import com.e1c.g5.v8.dt.check.settings.IssueSeverity;
import com.e1c.g5.v8.dt.check.settings.IssueType;
import com.e1c.v8codestyle.check.StandardCheckExtension;
import com.e1c.v8codestyle.internal.bsl.BslPlugin;

/**
 * 	check array modification in FillCheckProcessing()
 *  check find Delete() and Add() call in CheckedAttributes variable
 *  and set issue for that
 *
 *  @author Artem Samohvalov
 */
public class PropertiesArrayModificationCheck
    extends BasicCheck
{
    private static final String CHECKED_METHOD_NAME = "FillCheckProcessing"; //$NON-NLS-1$
    private static final String CHECKED_METHOD_NAME_RU = "ОбработкаПроверкиЗаполнения"; //$NON-NLS-1$

    private static final String EXCEPT_METHOD_NAME = "DeleteUncheckedAttributesFromArray"; //$NON-NLS-1$
    private static final String EXCEPT_METHOD_NAME_RU = "УдалитьНепроверяемыеРеквизитыИзМассива"; //$NON-NLS-1$

    private static final Set<String> CHECK_ADD_METHOD_CALLS = Set.of("add", "добавить"); //$NON-NLS-1$ //$NON-NLS-2$
    private static final Set<String> CHECK_DELETE_METHOD_CALLS = Set.of("delete", "удалить"); //$NON-NLS-1$ //$NON-NLS-2$

    private static class CheckContext
    {
        // on the top set of the 'target variables in lower case' for CURRENT method
        // go to method push set
        // return from the method pop set
        final Deque<Set<String>> targetVariableDeque = new ArrayDeque<>();
        final Set<String> targetGlobalVariables = new HashSet<>();

        Set<String> allGlobalVariableNames;
        ResultAcceptor resultAcceptor;
        Method currentMethod;
        String currentMethodName; // in lower case (for optimization)
        boolean isLastReturnTarget = false;
    }

    @Override
    public String getCheckId()
    {
        return "properties-array-modification"; //$NON-NLS-1$
    }

    @Override
    protected void configureCheck(CheckConfigurer builder)
    {
        builder.title(Messages.PropertiesArrayModificationCheck_description)
            .description(Messages.PropertiesArrayModificationCheck_title)
            .complexity(CheckComplexity.NORMAL)
            .severity(IssueSeverity.MINOR)
            .issueType(IssueType.CODE_STYLE)
            .extension(new ModuleTopObjectNameFilterExtension())
            .extension(new StandardCheckExtension(463, getCheckId(), BslPlugin.PLUGIN_ID))
            .module()
            .checkedObjectType(METHOD);
    }

    @Override
    protected void check(Object object, ResultAcceptor resultAcceptor, ICheckParameters parameters,
        IProgressMonitor monitor)
    {
        Method method = (Method)object;

        String methodName = method.getName().toLowerCase();
        if (methodName.equalsIgnoreCase(CHECKED_METHOD_NAME) || methodName.equalsIgnoreCase(CHECKED_METHOD_NAME_RU))
        {
            if (method.getFormalParams().size() != 2)
                return; // incorrect FillCheckProcessing

            CheckContext context = new CheckContext();
            context.resultAcceptor = resultAcceptor;
            context.currentMethod = method;
            context.currentMethodName = methodName;

            String checkedAttributesName = method.getFormalParams().get(1).getName().toLowerCase(); // get target variable, always on second position
            context.targetVariableDeque.push(new HashSet<>(Set.of(checkedAttributesName)));

            Module module = EcoreUtil2.getContainerOfType(method, Module.class);
            context.allGlobalVariableNames = module.allDeclareStatements()
                .stream()
                .flatMap(st -> st.getVariables().stream())
                .map(var -> var.getName().toLowerCase())
                .collect(Collectors.toSet());

            iterationByMethod(context);
        }
    }

    private void iterationByMethod(CheckContext context)
    {
        TreeIterator<EObject> it = EcoreUtil.getAllContents(context.currentMethod, true); // for parse all stmts

        while (it.hasNext())
        {
            EObject element = it.next();
            if (element instanceof ReturnStatement returnStatement)
            {
                processReturnValue(context, returnStatement);
                if (context.isLastReturnTarget)
                    return; // find target return
            }
            else if (element instanceof SimpleStatement simpleStatement)
            {
                processVariable(context, simpleStatement);
                processMethodCall(context, simpleStatement);
            }
        }
    }

    /**
     * add variable to targetVariableNames
     * if statement has two StaticFeatureAccess in left and right
     * and variable.name = TARGET_VARIABLE_NAME
     * 
     * need for tracking: MyVar = FillCheckProcessing;
     *                    MyVar.Delete(); // error
     * and:               MyVar = FillCheckProcessingRetFunc();
     *                    MyVar.Delete();
     * 
     * @param simpleStatement
     */
    private void processVariable(CheckContext context, SimpleStatement simpleStatement)
    {
        if (simpleStatement.getLeft() != null && simpleStatement.getRight() != null
            && simpleStatement.getLeft() instanceof StaticFeatureAccess leftStatement) // this is the var name
        {
            String lowerCaseLeftVarName = leftStatement.getName().toLowerCase();
            if (simpleStatement.getRight() instanceof StaticFeatureAccess rightStatement) // this is the assignable value
            {
                String lowerCaseRightVarName = rightStatement.getName().toLowerCase();
                // remove if target = some_not_target
                if (containsInTargets(context, lowerCaseLeftVarName)
                    && !containsInTargets(context, lowerCaseRightVarName))
                {
                    removeFromVariableDeque(context, lowerCaseLeftVarName);
                    return;
                }

                // add if some_not_target = target
                if (containsInTargets(context, lowerCaseRightVarName))
                {
                    if (context.allGlobalVariableNames.contains(lowerCaseLeftVarName))
                    {
                        context.targetGlobalVariables.add(lowerCaseLeftVarName);
                        return;
                    }
                    // set new target variable name
                    addToVariableDeque(context, lowerCaseLeftVarName);
                }
            }
            else if (containsInVariableDeque(context, lowerCaseLeftVarName)) // delete if target = some_expr
            {
                removeFromVariableDeque(context, lowerCaseLeftVarName);
            }
            else if (context.targetGlobalVariables.contains(lowerCaseLeftVarName)) // delete if glob_target = some_expr
            {
                context.targetGlobalVariables.remove(lowerCaseLeftVarName);
            }
            else if (simpleStatement.getRight() instanceof Invocation invocationExpression
                && invocationExpression.getMethodAccess() instanceof StaticFeatureAccess) // common method call on the right
            {
                goToMethod(context, invocationExpression, true);
                if (context.isLastReturnTarget)
                {
                    addToVariableDeque(context, lowerCaseLeftVarName);
                }
            }
        }
    }

    /**
     * the method searches for the calls being checked (Delete, Add) and add the issue
     * 
     * example: SomeTargetVariable.Delete() // error
     * 
     * @param simpleStatement
     * @param resultAcceptor
     */
    private void processMethodCall(CheckContext context, SimpleStatement simpleStatement)
    {
        if (simpleStatement.getLeft() != null && simpleStatement.getLeft() instanceof Invocation invocationExpression)
        {
            if (invocationExpression.getMethodAccess() instanceof DynamicFeatureAccess dynamicAccess) // if access from '.' => this is method call
            {
                // for - var1.Delete()
                if (dynamicAccess.getSource() instanceof StaticFeatureAccess staticAccess) // if source is variable 
                {
                    String lowerMethodName = dynamicAccess.getName().toLowerCase();
                    String lowerVariableName = staticAccess.getName().toLowerCase();
                    if (containsInVariableDeque(context, lowerVariableName)
                        || context.targetGlobalVariables.contains(lowerVariableName)) // if target variable
                    {
                        setIssueByMethodName(context, lowerMethodName, simpleStatement);
                    }
                }
                // for - SomeMethod().Delete(); don't work for - SomeMethod().SomeMethod().Delete()
                else if (dynamicAccess.getSource() instanceof Invocation subMethodInvocation
                    && invocationExpression.getMethodAccess() instanceof DynamicFeatureAccess subDynamicAccess) // method call
                {
                    goToMethod(context, subMethodInvocation, true);
                    if (context.isLastReturnTarget)
                    {
                        setIssueByMethodName(context, subDynamicAccess.getName(), simpleStatement);
                    }
                }
            }
            else
            {
                // common method call
                goToMethod(context, invocationExpression, false);
            }
        }
    }

    /**
     * need for tracking: MyVar = ReturnTarget(); // this is target
     * 
     * @param returnStatement
     */
    private void processReturnValue(CheckContext context, ReturnStatement returnStatement)
    {
        if (returnStatement.getExpression() instanceof StaticFeatureAccess staticAccess)
        {
            String lowerVariableName = staticAccess.getName().toLowerCase();
            if (context.targetGlobalVariables.contains(lowerVariableName)
                || containsInVariableDeque(context, lowerVariableName))
            {
                context.isLastReturnTarget = true;
                return;
            }
        }
        if (returnStatement.getExpression() instanceof Invocation invocationExpression)
        {
            goToMethod(context, invocationExpression, false);
            return;
        }

        context.isLastReturnTarget = false;
    }

    /**
     * go to method 
     * if method has FillCheckProcessing (or it reference) in arguments
     *      or some global var has target reference
     * 
     * @param simpleStatement
     */
    private void goToMethod(CheckContext context, Invocation invocationExpression, boolean onlyFunction)
    {
        String methodName = invocationExpression.getMethodAccess().getName();
        if (!methodName.equalsIgnoreCase(context.currentMethodName)) // if not recursion
        {
            var optionalMethod = findMethodByName(context, methodName);
            if (optionalMethod.isPresent()) // if method name exists in module
            {
                if (onlyFunction && !(optionalMethod.get() instanceof Function)) // if is not function
                    return;

                List<Integer> targetVariablePositions = new ArrayList<>();
                var params = invocationExpression.getParams();

                for (int i = 0; i < params.size(); ++i)
                {
                    if (params.get(i) instanceof StaticFeatureAccess sfa)
                    {
                        if (containsInVariableDeque(context, sfa.getName().toLowerCase()))
                        {
                            targetVariablePositions.add(i);
                        }
                    }
                }

                // if args has a target variable
                // or there is a global target variable here
                if (targetVariablePositions.size() > 0 || !context.targetGlobalVariables.isEmpty())
                {
                    context.isLastReturnTarget = false;

                    Method lastMethod = context.currentMethod;

                    changeCurrentMethod(context, optionalMethod.get());
                    updateTargetVariablesForMethod(context, targetVariablePositions);
                    iterationByMethod(context);
                    // method ends

                    context.targetVariableDeque.pop(); // method ends, targets don't needs
                    changeCurrentMethod(context, lastMethod);
                }
            }
        }
    }

    private void changeCurrentMethod(CheckContext context, Method method)
    {
        context.currentMethod = method;
        context.currentMethodName = method.getName().toLowerCase();
    }

    /**
     * situation:
     * method call      -> CallMethod(MyTargetValue, 123, MySecondTargetValue, "hello") => target is 0 and 2
     * method signature -> CallMethod(A, B, C, D) => A and C is target variables
     * 
     * @param targetPositions
     */
    private void updateTargetVariablesForMethod(CheckContext context, List<Integer> targetPositions)
    {
        var argumentList = context.currentMethod.getFormalParams();

        Set<String> targetSet = new HashSet<>();
        for (int i = 0; i < argumentList.size(); ++i)
        {
            if (targetPositions.contains(i))
            {
                targetSet.add(argumentList.get(i).getName().toLowerCase());
            }
        }

        context.targetVariableDeque.push(targetSet);
    }

    private void setIssueByMethodName(CheckContext context, String methodName, Statement statement)
    {
        String lowerMethodName = methodName.toLowerCase();
        if (CHECK_ADD_METHOD_CALLS.contains(lowerMethodName))
        {
            context.resultAcceptor.addIssue(Messages.PropertiesArrayModificationCheck_add_issue, statement);
        }
        else if (CHECK_DELETE_METHOD_CALLS.contains(lowerMethodName)
            && !context.currentMethodName.equalsIgnoreCase(EXCEPT_METHOD_NAME)
            && !context.currentMethodName.equalsIgnoreCase(EXCEPT_METHOD_NAME_RU))
        {
            context.resultAcceptor.addIssue(Messages.PropertiesArrayModificationCheck_delete_issue, statement);
        }
    }

    private boolean containsInVariableDeque(CheckContext context, String lowerCaseVariableName)
    {
        return !context.targetVariableDeque.isEmpty()
            && context.targetVariableDeque.peek().contains(lowerCaseVariableName);
    }

    private boolean containsInTargets(CheckContext context, String lowerCaseVariableName)
    {
        return (!context.targetVariableDeque.isEmpty()
            && (context.targetVariableDeque.peek().contains(lowerCaseVariableName)))
            || context.targetGlobalVariables.contains(lowerCaseVariableName);
    }

    private void addToVariableDeque(CheckContext context, String lowerCaseVariableName)
    {
        if (!context.targetVariableDeque.isEmpty())
        {
            context.targetVariableDeque.peek().add(lowerCaseVariableName);
        }
    }

    private void removeFromVariableDeque(CheckContext context, String lowerCaseVariableName)
    {
        if (!context.targetVariableDeque.isEmpty())
        {
            context.targetVariableDeque.peek().remove(lowerCaseVariableName);
        }
    }

    public Optional<Method> findMethodByName(CheckContext context, String methodName)
    {
        Module module = EcoreUtil2.getContainerOfType(context.currentMethod, Module.class);
        return module.allMethods().stream().filter(m -> m.getName().equalsIgnoreCase(methodName)).findAny();
    }
}
