package graphql.validation;


import graphql.masker.Internal;
import graphql.language.node.Argument;
import graphql.language.node.Directive;
import graphql.language.Document;
import graphql.language.node.Field;
import graphql.language.node.definition.FragmentDefinition;
import graphql.language.node.FragmentSpread;
import graphql.language.node.InlineFragment;
import graphql.language.node.Node;
import graphql.language.node.definition.OperationDefinition;
import graphql.language.node.SelectionSet;
import graphql.language.node.SourceLocation;
import graphql.language.node.TypeName;
import graphql.language.node.definition.VariableDefinition;
import graphql.language.node.VariableReference;

import java.util.ArrayList;
import java.util.List;

import static graphql.validation.ValidationError.newValidationError;

@Internal
public class AbstractRule {

    private final ValidationContext validationContext;
    private final ValidationErrorCollector validationErrorCollector;


    private boolean visitFragmentSpreads;

    private ValidationUtil validationUtil = new ValidationUtil();

    public AbstractRule(ValidationContext validationContext, ValidationErrorCollector validationErrorCollector) {
        this.validationContext = validationContext;
        this.validationErrorCollector = validationErrorCollector;
    }

    public boolean isVisitFragmentSpreads() {
        return visitFragmentSpreads;
    }

    public void setVisitFragmentSpreads(boolean visitFragmentSpreads) {
        this.visitFragmentSpreads = visitFragmentSpreads;
    }


    public ValidationUtil getValidationUtil() {
        return validationUtil;
    }

    public void addError(ValidationErrorType validationErrorType, List<? extends Node<?>> locations, String description) {
        List<SourceLocation> locationList = new ArrayList<>();
        for (Node<?> node : locations) {
            locationList.add(node.getSourceLocation());
        }
        addError(newValidationError()
                .validationErrorType(validationErrorType)
                .sourceLocations(locationList)
                .description(description));
    }

    public void addError(ValidationErrorType validationErrorType, SourceLocation location, String description) {
        addError(newValidationError()
                .validationErrorType(validationErrorType)
                .sourceLocation(location)
                .description(description));
    }

    public void addError(ValidationError.Builder validationError) {
        validationErrorCollector.addError(validationError.queryPath(getQueryPath()).build());
    }

    public List<ValidationError> getErrors() {
        return validationErrorCollector.getErrors();
    }


    public ValidationContext getValidationContext() {
        return validationContext;
    }

    public ValidationErrorCollector getValidationErrorCollector() {
        return validationErrorCollector;
    }

    protected List<String> getQueryPath() {
        return validationContext.getQueryPath();
    }

    public void checkDocument(Document document) {

    }

    public void checkArgument(Argument argument) {

    }

    public void checkTypeName(TypeName typeName) {

    }

    public void checkVariableDefinition(VariableDefinition variableDefinition) {

    }

    public void checkField(Field field) {

    }

    public void checkInlineFragment(InlineFragment inlineFragment) {

    }

    public void checkDirective(Directive directive, List<Node> ancestors) {

    }

    public void checkFragmentSpread(FragmentSpread fragmentSpread) {

    }

    public void checkFragmentDefinition(FragmentDefinition fragmentDefinition) {

    }

    public void checkOperationDefinition(OperationDefinition operationDefinition) {

    }

    public void leaveOperationDefinition(OperationDefinition operationDefinition) {

    }

    public void checkSelectionSet(SelectionSet selectionSet) {

    }

    public void leaveSelectionSet(SelectionSet selectionSet) {

    }

    public void checkVariable(VariableReference variableReference) {

    }

    public void documentFinished(Document document) {

    }

    @Override
    public String toString() {
        return "Rule{" + validationContext + "}";
    }
}
