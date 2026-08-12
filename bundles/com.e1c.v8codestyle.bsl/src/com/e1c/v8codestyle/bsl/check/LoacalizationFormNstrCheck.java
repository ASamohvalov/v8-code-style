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

import static com._1c.g5.v8.dt.bsl.model.BslPackage.Literals.MODULE;

import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.xtext.EcoreUtil2;

import com._1c.g5.v8.dt.bsl.model.DynamicFeatureAccess;
import com._1c.g5.v8.dt.bsl.model.EmptyStatement;
import com._1c.g5.v8.dt.bsl.model.ForStatement;
import com._1c.g5.v8.dt.bsl.model.IfStatement;
import com._1c.g5.v8.dt.bsl.model.Invocation;
import com._1c.g5.v8.dt.bsl.model.Method;
import com._1c.g5.v8.dt.bsl.model.Module;
import com._1c.g5.v8.dt.bsl.model.ModuleType;
import com._1c.g5.v8.dt.bsl.model.SimpleStatement;
import com._1c.g5.v8.dt.bsl.model.Statement;
import com._1c.g5.v8.dt.bsl.model.StaticFeatureAccess;
import com._1c.g5.v8.dt.bsl.model.StringLiteral;
import com._1c.g5.v8.dt.bsl.model.util.BslUtil;
import com._1c.g5.v8.dt.form.model.Form;
import com._1c.g5.v8.dt.form.model.FormAttribute;
import com._1c.g5.v8.dt.form.model.FormAttributeColumn;
import com._1c.g5.v8.dt.mcore.TypeItem;
import com._1c.g5.v8.dt.mcore.util.McoreUtil;
import com.e1c.g5.v8.dt.check.CheckComplexity;
import com.e1c.g5.v8.dt.check.ICheckParameters;
import com.e1c.g5.v8.dt.check.settings.IssueSeverity;
import com.e1c.g5.v8.dt.check.settings.IssueType;
import com.e1c.v8codestyle.check.StandardCheckExtension;
import com.e1c.v8codestyle.internal.bsl.BslPlugin;

/**
 * Checks that variable is self assign.
 *
 *  @author Ivan Sergeev
 */
public class LoacalizationFormNstrCheck
    extends AbstractModuleStructureCheck
{
    private static final String CHECK_ID = "nstr-form-localization"; //$NON-NLS-1$

    private static final String NSTR = "NStr"; //$NON-NLS-1$

    private static final String NSTR_RU = "НСтр"; //$NON-NLS-1$

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
            .extension(new StandardCheckExtension(761, getCheckId(), BslPlugin.PLUGIN_ID))
            .module()
            .checkedObjectType(MODULE);
    }

    @Override
    protected void check(Object object, ResultAcceptor resultAceptor, ICheckParameters parameters,
        IProgressMonitor monitor)
    {
        Module formModule = (Module)object;
        if (ModuleType.FORM_MODULE != formModule.getModuleType())
        {
            return;
        }
        Form form = (Form)formModule.getOwner();
        List<FormAttribute> attributes = form.getAttributes();
        for (FormAttribute attribute : attributes)
        {
            List<TypeItem> types = attribute.getValueType().getTypes();
            for (TypeItem type : types)
            {
                if ("String".equalsIgnoreCase(McoreUtil.getTypeName(type))) //$NON-NLS-1$
                {
                    List<Method> methods = BslUtil.allMethods(formModule);
                    for (Method method : methods)
                    {
                        List<Statement> statements = method.allStatements();
                        Statement statement = searchStatement(statements, attribute.getName());
                        if (statement != null)
                        {
                            if (checkStatement(statement))
                            {
                                resultAceptor.addIssue(Messages.LoacalizationNstrCheck_Issue, statement);
                            }
                        }
                    }
                }
                else if ("ValueTable".equalsIgnoreCase(McoreUtil.getTypeName(type))) //$NON-NLS-1$
                {
                    List<FormAttributeColumn> columns = attribute.getColumns();
                    List<Method> methods = BslUtil.allMethods(formModule);
                    for (FormAttributeColumn column : columns)
                    {
                        for (Method method : methods)
                        {
                            List<Statement> statements = method.allStatements();
                            Statement statement = searchStatement(statements, column.getName());
                            if (statement != null)
                            {
                                if (checkStatement(statement))
                                {
                                    resultAceptor.addIssue(Messages.LoacalizationNstrCheck_Issue, statement);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean checkStatement(Statement statement)
    {
        if (statement instanceof SimpleStatement simpleStat)
        {
            if (simpleStat.getRight() instanceof StringLiteral)
            {
                return true;
            }
            else if (simpleStat.getRight() instanceof StaticFeatureAccess sfa)
            {
                String name = sfa.getName();
                Method method = EcoreUtil2.getContainerOfType(statement, Method.class);
                if (!checkSfa(name, method))
                {
                    return true;
                }
            }
            else if (statement instanceof Invocation invocationParam)
            {
                String name = invocationParam.getMethodAccess().getName();
                if (!(NSTR_RU.equalsIgnoreCase(name) || NSTR.equalsIgnoreCase(name)))
                {
                    return true;
                }
            }
        }
        return false;
    }

    private Statement searchStatement(List<Statement> statemtnts, String attributeName)
    {
        for (Statement statement : statemtnts)
        {
            if (statement instanceof EmptyStatement)
            {
                continue;
            }
            else if (statement instanceof SimpleStatement simp)
            {
                if (simp.getLeft() instanceof DynamicFeatureAccess left)
                {
                    if (left.getName().equalsIgnoreCase(attributeName))
                    {
                        return statement;
                    }
                }
            }
            else if (statement instanceof IfStatement ifStatement)
            {
                List<Statement> ifStatements = ifStatement.getIfPart().getStatements();
                Statement stat = searchStatement(ifStatements, attributeName);
                if (stat != null)
                {
                    return stat;
                }
                List<Statement> elseStatements = ifStatement.getElseStatements();
                stat = searchStatement(elseStatements, attributeName);
                if (stat != null)
                {
                    return stat;
                }
            }
            else if (statement instanceof ForStatement forStatement)
            {
                List<Statement> forStatements = forStatement.getStatements();
                Statement stat = searchStatement(forStatements, attributeName);
                if (stat != null)
                {
                    return stat;
                }
            }
        }
        return null;
    }

    private boolean checkSfa(String name, Method method)
    {
        List<Statement> statements = method.allStatements();
        for (Statement statement : statements)
        {
            if (statement instanceof SimpleStatement simpState)
            {
                if (simpState.getLeft() instanceof StaticFeatureAccess left)
                {
                    if (left.getName().equalsIgnoreCase(name))
                    {
                        if (simpState.getRight() instanceof Invocation invocation)
                        {
                            String nameInv = invocation.getMethodAccess().getName();
                            if (NSTR_RU.equalsIgnoreCase(nameInv) || NSTR.equalsIgnoreCase(nameInv))
                            {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }
}
