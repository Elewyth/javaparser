package com.github.javaparser.symbolsolver.javaparsermodel.declarations;

import com.github.javaparser.ast.AccessSpecifier;
import com.github.javaparser.resolution.declarations.ResolvedConstructorDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedParameterDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedRecordDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedTypeParameterDeclaration;
import com.github.javaparser.resolution.types.ResolvedType;

import java.util.List;

public class CanonicalRecordConstructorDeclaration implements ResolvedConstructorDeclaration {

    private ResolvedRecordDeclaration declaringType;

    CanonicalRecordConstructorDeclaration(ResolvedRecordDeclaration declaringType) {
        this.declaringType = declaringType;
    }

    @Override
    public AccessSpecifier accessSpecifier() {
        return AccessSpecifier.PUBLIC;
    }

    @Override
    public ResolvedRecordDeclaration declaringType() {
        return declaringType;
    }

    @Override
    public int getNumberOfParams() {
        return declaringType.getRecordComponents().size();
    }

    @Override
    public ResolvedParameterDeclaration getParam(int i) {
        return declaringType.getRecordComponents().get(i);
    }

    @Override
    public int getNumberOfSpecifiedExceptions() {
        return 0;
    }

    @Override
    public ResolvedType getSpecifiedException(int index) {
        throw new UnsupportedOperationException("The canonical record constructor does not throw exceptions");
    }

    @Override
    public String getName() {
        return declaringType.getName();
    }

    @Override
    public List<ResolvedTypeParameterDeclaration> getTypeParameters() {
        return declaringType.getTypeParameters();
    }
}
