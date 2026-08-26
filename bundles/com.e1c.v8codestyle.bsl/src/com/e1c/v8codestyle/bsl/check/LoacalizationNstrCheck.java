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

import static com._1c.g5.v8.dt.bsl.model.BslPackage.Literals.INVOCATION;

import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.xtext.EcoreUtil2;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;

import com._1c.g5.v8.dt.bsl.model.Expression;
import com._1c.g5.v8.dt.bsl.model.ForStatement;
import com._1c.g5.v8.dt.bsl.model.IfStatement;
import com._1c.g5.v8.dt.bsl.model.Invocation;
import com._1c.g5.v8.dt.bsl.model.Method;
import com._1c.g5.v8.dt.bsl.model.SimpleStatement;
import com._1c.g5.v8.dt.bsl.model.Statement;
import com._1c.g5.v8.dt.bsl.model.StaticFeatureAccess;
import com._1c.g5.v8.dt.bsl.model.StringLiteral;
import com.e1c.g5.v8.dt.check.CheckComplexity;
import com.e1c.g5.v8.dt.check.ICheckParameters;
import com.e1c.g5.v8.dt.check.components.ModuleTopObjectNameFilterExtension;
import com.e1c.g5.v8.dt.check.settings.IssueSeverity;
import com.e1c.g5.v8.dt.check.settings.IssueType;
import com.e1c.v8codestyle.check.StandardCheckExtension;
import com.e1c.v8codestyle.internal.bsl.BslPlugin;

/**
 * Checks string localization use Nstr.
 *
 *  @author Ivan Sergeev
 */
public class LoacalizationNstrCheck
    extends AbstractModuleStructureCheck
{
    private static final String CHECK_ID = "nstr-localization"; //$NON-NLS-1$

    private static final String MESSAGE_NAME = "Message name"; //$NON-NLS-1$

    private static final String MESSAGE_NAME_ONE = "Message name parameter number one"; //$NON-NLS-1$

    private static final String MESSAGE_NAME_ZERO = "Message name parameter number zero"; //$NON-NLS-1$

    private static final String NSTR = "NStr"; //$NON-NLS-1$

    private static final String NSTR_RU = "НСтр"; //$NON-NLS-1$

    private static final Set<String> IMMUTABLE_MAP_MESSAGES =
        Set.of("ПоказатьПредупреждение", "ShowMessagebox", "Сообщение", "Message", "Сообщить", //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$//$NON-NLS-4$//$NON-NLS-5$
            "ПоказатьОповещениеПользователя", "ShowUserNotification", "ПоказатьВопрос", "ShowQueryBox", "Состояние", //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$//$NON-NLS-4$ //$NON-NLS-5$
            "Status"); //$NON-NLS-1$
    private static final Set<String> IMMUTABLE_MAP_NUMBER_ONE_MESSAGES =
        Set.of("ПоказатьПредупреждение", "ShowMessageBox", "ПоказатьВопрос", "ShowQueryBox"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

    private static final Set<String> IMMUTABLE_MAP_NUMBER_ZERO_MESSAGES = Set.of("сообщение", "Сообщить", "Message", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        "ПоказатьОповещениеПользователя", "ShowUsernotification", "Состояние", "Status"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

    private static final String DELIMITER = ","; //$NON-NLS-1$

    private static final String DEFAULT_MESSAGES = String.join(DELIMITER, IMMUTABLE_MAP_MESSAGES);

    private static final String DEFAULT_MESSAGES_NUMBER_ONE = String.join(DELIMITER, IMMUTABLE_MAP_NUMBER_ONE_MESSAGES);

    private static final String DEFAULT_MESSAGES_NUMBER_ZERO =
        String.join(DELIMITER, IMMUTABLE_MAP_NUMBER_ZERO_MESSAGES);
    @Override
    public String getCheckId()
    {
        return CHECK_ID;
    }

    @Override
    protected void configureCheck(CheckConfigurer builder)
    {
        builder.title(Messages.LoacalizationNstrCheck_Title)
            .description(Messages.LoacalizationNstrCheck_Description)
            .complexity(CheckComplexity.NORMAL)
            .severity(IssueSeverity.MINOR)
            .issueType(IssueType.CODE_STYLE)
            .extension(new ModuleTopObjectNameFilterExtension())
            .extension(new StandardCheckExtension(761, getCheckId(), BslPlugin.PLUGIN_ID))
            .parameter(MESSAGE_NAME, String.class, DEFAULT_MESSAGES, Messages.LoacalizationNstrCheck_Parameter_Title)
            .parameter(MESSAGE_NAME_ONE, String.class, DEFAULT_MESSAGES_NUMBER_ONE,
                Messages.LoacalizationNstrCheck_Parameter_Title_One)
            .parameter(MESSAGE_NAME_ZERO, String.class, DEFAULT_MESSAGES_NUMBER_ZERO,
                Messages.LoacalizationNstrCheck_Parameter_Title_Zero)
            .module()
            .checkedObjectType(INVOCATION);
    }

    @Override
    protected void check(Object object, ResultAcceptor resultAceptor, ICheckParameters parameters,
        IProgressMonitor monitor)
    {
        Invocation invocation = (Invocation)object;
        NodeModelUtils.findActualNodeFor(invocation).getText();
        if (!parameters.getString(MESSAGE_NAME)
            .toLowerCase()
            .contains(invocation.getMethodAccess().getName().toLowerCase())
            || invocation.getParams().isEmpty())
        {
            return;
        }
        List<Expression> params = invocation.getParams();
        String nameInvocation = invocation.getMethodAccess().getName();
        int numberParam = numberParametr(nameInvocation, parameters);
        if (numberParam != -1)
        {
            Expression expression = params.get(numberParam);
            if (expression instanceof StringLiteral stingLiteral)
            {
                resultAceptor.addIssue(Messages.LoacalizationNstrCheck_Issue);
            }
            else if (expression instanceof StaticFeatureAccess sfa)
            {
                String name = sfa.getName();
                Method method = EcoreUtil2.getContainerOfType(invocation, Method.class);
                List<Statement> statements = method.allStatements();
                if (!checkSfa(name, statements))
                {
                    resultAceptor.addIssue(Messages.LoacalizationNstrCheck_Issue);
                }
            }
            else if (expression instanceof Invocation invocationParam)
            {
                String name = invocationParam.getMethodAccess().getName();
                if (!(NSTR_RU.equalsIgnoreCase(name) || NSTR.equalsIgnoreCase(name)))
                {
                    if (!invocationParam.getParams().isEmpty())
                    {
                        List<Expression> parametrs = invocationParam.getParams();
                        Expression param = parametrs.get(0);
                        if (param instanceof Invocation inv)
                        {
                            String invName = inv.getMethodAccess().getName();
                            if (!(NSTR_RU.equalsIgnoreCase(invName) || NSTR.equalsIgnoreCase(invName)))
                            {
                                resultAceptor.addIssue(Messages.LoacalizationNstrCheck_Issue);
                            }
                        }
                    }
                }
            }
        }
    }

    private int numberParametr(String name, ICheckParameters parameters)
    {
        if (parameters.getString(MESSAGE_NAME_ONE).toLowerCase().contains(name.toLowerCase()))
        {
            return 1;
        }
        else if (parameters.getString(MESSAGE_NAME_ZERO).toLowerCase().contains(name.toLowerCase()))
        {
            return 0;
        }
        return -1;
    }

    private boolean checkSfa(String name, List<Statement> statements)
    {
        for (Statement statement : statements)
        {
            if (statement instanceof SimpleStatement simpState)
            {
                if (checkSimpleState(simpState, name))
                {
                    return true;
                }
            }
            else if (statement instanceof IfStatement ifStatement)
            {
                List<Statement> ifStatements = ifStatement.getIfPart().getStatements();
                if (checkSfa(name, ifStatements))
                {
                    return true;
                }
                List<Statement> elseStatements = ifStatement.getElseStatements();
                if (checkSfa(name, elseStatements))
                {
                    return true;
                }
            }
            else if (statement instanceof ForStatement forStatement)
            {
                List<Statement> forStatements = forStatement.getStatements();
                if (checkSfa(name, forStatements))
                {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean checkSimpleState(SimpleStatement statement, String name)
    {
        if (statement.getLeft() instanceof StaticFeatureAccess left)
        {
            NodeModelUtils.findActualNodeFor(statement).getText();
            if (left.getName().equalsIgnoreCase(name))
            {
                if (statement.getRight() instanceof Invocation invocation)
                {
                    String nameInv = invocation.getMethodAccess().getName();
                    if (NSTR_RU.equalsIgnoreCase(nameInv) || NSTR.equalsIgnoreCase(nameInv))
                    {
                        return true;
                    }
                    else if (!invocation.getParams().isEmpty())
                    {
                        List<Expression> params = invocation.getParams();
                        for (Expression param : params)
                        {
                            if (param instanceof Invocation inv)
                            {
                                String invName = inv.getMethodAccess().getName();
                                if (NSTR_RU.equalsIgnoreCase(invName) || NSTR.equalsIgnoreCase(invName))
                                {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }
}
