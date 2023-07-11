package com.github.javaparser.resolution.declarations;

import com.github.javaparser.resolution.types.ResolvedReferenceType;

import java.util.List;

public interface ResolvedRecordDeclaration extends ResolvedReferenceTypeDeclaration, ResolvedTypeParametrizable, HasAccessSpecifier {

    @Override
    default boolean isRecord() {
        return true;
    }

    /**
     * Return all the interfaces implemented directly by this record.
     */
    List<ResolvedReferenceType> getInterfaces();

    /**
     * List of constructors available for the record.
     * This list should also include the default constructor.
     */
    @Override
    List<ResolvedConstructorDeclaration> getConstructors();

    /**
     * List of record components, i.e. the non-static fields of the record.
     */
    List<ResolvedParameterDeclaration> getRecordComponents();
}
