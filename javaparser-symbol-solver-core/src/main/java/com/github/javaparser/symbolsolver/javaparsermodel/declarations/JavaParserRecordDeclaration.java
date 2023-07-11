package com.github.javaparser.symbolsolver.javaparsermodel.declarations;

import com.github.javaparser.ast.AccessSpecifier;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.resolution.Context;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.declarations.*;
import com.github.javaparser.resolution.model.SymbolReference;
import com.github.javaparser.resolution.model.typesystem.LazyType;
import com.github.javaparser.resolution.model.typesystem.ReferenceTypeImpl;
import com.github.javaparser.resolution.types.ResolvedReferenceType;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade;
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFactory;
import com.github.javaparser.symbolsolver.logic.AbstractTypeDeclaration;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

public class JavaParserRecordDeclaration extends AbstractTypeDeclaration implements ResolvedRecordDeclaration {

    private final RecordDeclaration wrappedNode;
    private final TypeSolver typeSolver;
    private final JavaParserTypeAdapter<RecordDeclaration> javaParserTypeAdapter;

    public JavaParserRecordDeclaration(RecordDeclaration wrappedNode, TypeSolver typeSolver) {
        this.wrappedNode = wrappedNode;
        this.typeSolver = typeSolver;
        this.javaParserTypeAdapter = new JavaParserTypeAdapter<>(wrappedNode, typeSolver);
    }

    @Override
    public String getName() {
        return wrappedNode.getNameAsString();
    }

    @Override
    public List<ResolvedReferenceType> getAncestors(boolean acceptIncompleteList) {
        List<ResolvedReferenceType> ancestors = new ArrayList<>();

        // We want to avoid infinite recursion in case of Object having Object as ancestor
        if (this.isJavaLangObject()) {
            return ancestors;
        }

        Optional<String> qualifiedName = wrappedNode.getFullyQualifiedName();
        if (!qualifiedName.isPresent()) {
            return ancestors;
        }

        try {
            // If a superclass is found, add it as an ancestor
            ResolvedReferenceType superClass = new ReferenceTypeImpl(typeSolver.solveType("java.lang.Record"));
            if (isAncestor(superClass, qualifiedName.get())) {
                ancestors.add(superClass);
            }
        } catch (UnsolvedSymbolException e) {
            // in case we could not resolve the super class, we may still be able to resolve (some of) the
            // implemented interfaces and so we continue gracefully with an (incomplete) list of ancestors

            if (!acceptIncompleteList) {
                // Only throw if an incomplete ancestor list is unacceptable.
                throw e;
            }
        }

        for (ClassOrInterfaceType implemented : wrappedNode.getImplementedTypes()) {
            try {
                // If an implemented interface is found, add it as an ancestor
                ResolvedReferenceType rrt = toReferenceType(implemented);
                if (isAncestor(rrt, qualifiedName.get())) {
                    ancestors.add(rrt);
                }
            } catch (UnsolvedSymbolException e) {
                // in case we could not resolve some implemented interface, we may still be able to resolve the
                // extended class or (some of) the other implemented interfaces and so we continue gracefully
                // with an (incomplete) list of ancestors

                if (!acceptIncompleteList) {
                    // Only throw if an incomplete ancestor list is unacceptable.
                    throw e;
                }
            }
        }

        return ancestors;
    }

    private ResolvedReferenceType toReferenceType(ClassOrInterfaceType classOrInterfaceType) {
        String className = classOrInterfaceType.getName().getId();
        Optional<ClassOrInterfaceType> scope = classOrInterfaceType.getScope();
        if (scope.isPresent()) {
            // look for the qualified name (for example class of type Rectangle2D.Double)
            className = scope.get() + "." + className;
        }
        SymbolReference<ResolvedTypeDeclaration> ref = solveType(className);

        // If unable to solve by the class name alone, attempt to qualify it.
        if (!ref.isSolved()) {
            Optional<ClassOrInterfaceType> localScope = classOrInterfaceType.getScope();
            if (localScope.isPresent()) {
                String localName = localScope.get().getName().getId() + "." + classOrInterfaceType.getName().getId();
                ref = solveType(localName);
            }
        }

        // If still unable to resolve, throw an exception.
        if (!ref.isSolved()) {
            throw new UnsolvedSymbolException(classOrInterfaceType.getName().getId());
        }

        Optional<NodeList<Type>> typeArguments = classOrInterfaceType.getTypeArguments();
        if (!typeArguments.isPresent()) {
            return new ReferenceTypeImpl(ref.getCorrespondingDeclaration().asReferenceType());
        }

        List<ResolvedType> superClassTypeParameters = typeArguments.get().stream()
                .map(ta -> new LazyType(v -> JavaParserFacade.get(typeSolver).convert(ta, ta)))
                .collect(toList());

        return new ReferenceTypeImpl(ref.getCorrespondingDeclaration().asReferenceType(), superClassTypeParameters);
    }

    private boolean isAncestor(ResolvedReferenceType candidateAncestor, String ownQualifiedName) {
        Optional<ResolvedReferenceTypeDeclaration> resolvedReferenceTypeDeclaration = candidateAncestor.getTypeDeclaration();
        if (resolvedReferenceTypeDeclaration.isPresent()) {
            ResolvedTypeDeclaration rtd = resolvedReferenceTypeDeclaration.get().asType();
            // do not consider an inner or nested class as an ancestor
            return !rtd.hasInternalType(ownQualifiedName);
        }
        return false;
    }

    /**
     * Resolution should move out of declarations, so that they are pure declarations and the resolution should
     * work for JavaParser, Reflection and Javassist classes in the same way and not be specific to the three
     * implementations.
     */
    @Deprecated
    public SymbolReference<ResolvedTypeDeclaration> solveType(String name) {
        if (this.wrappedNode.getName().getId().equals(name)) {
            return SymbolReference.solved(this);
        }
        SymbolReference<ResolvedTypeDeclaration> ref = javaParserTypeAdapter.solveType(name);
        if (ref.isSolved()) {
            return ref;
        }

        String prefix = wrappedNode.getName().asString() + ".";
        if (name.startsWith(prefix) && name.length() > prefix.length()) {
            return new JavaParserRecordDeclaration(this.wrappedNode, typeSolver).solveType(name.substring(prefix.length()));
        }

        return getContext().getParent()
                .orElseThrow(() -> new RuntimeException("Parent context unexpectedly empty."))
                .solveType(name);
    }

    private Context getContext() {
        return JavaParserFactory.getContext(wrappedNode, typeSolver);
    }

    @Override
    public List<ResolvedFieldDeclaration> getAllFields() {
        return wrappedNode.getFields().stream()
                .flatMap(f -> f.getVariables().stream())
                .map(v -> new JavaParserFieldDeclaration(v, typeSolver))
                .collect(toList());
    }

    public List<ResolvedParameterDeclaration> getRecordComponents() {
        return wrappedNode.getParameters().stream()
                .map(p -> new JavaParserParameterDeclaration(p, typeSolver))
                .collect(toList());
    }

    @Override
    public Set<ResolvedMethodDeclaration> getDeclaredMethods() {
        return wrappedNode.getMethods().stream()
                .map(m -> new JavaParserMethodDeclaration(m, typeSolver))
                .collect(Collectors.toSet());
    }

    @Override
    public boolean isAssignableBy(ResolvedType type) {
        return javaParserTypeAdapter.isAssignableBy(type);
    }

    @Override
    public boolean isAssignableBy(ResolvedReferenceTypeDeclaration other) {
        return javaParserTypeAdapter.isAssignableBy(other);
    }

    @Override
    public boolean hasDirectlyAnnotation(String qualifiedName) {
        return AstResolutionUtils.hasDirectlyAnnotation(wrappedNode, typeSolver, qualifiedName);
    }

    @Override
    public List<ResolvedConstructorDeclaration> getConstructors() {
        boolean explicitAllArgsConstructor = false;

        List<ResolvedConstructorDeclaration> constructors = new ArrayList<>();
        for (ConstructorDeclaration ctor : wrappedNode.getConstructors()) {
            constructors.add(new JavaParserConstructorDeclaration<ResolvedRecordDeclaration>(this, ctor, typeSolver));
        }

        if (!explicitAllArgsConstructor) {
            // TODO: add generated all-args constructor, if not declared explicitly
            constructors.add(new CanonicalRecordConstructorDeclaration(this));
        }
        return constructors;
    }

    @Override
    public Optional<ResolvedReferenceTypeDeclaration> containerType() {
        return javaParserTypeAdapter.containerType();
    }

    @Override
    public String getPackageName() {
        return javaParserTypeAdapter.getPackageName();
    }

    @Override
    public String getClassName() {
        return javaParserTypeAdapter.getClassName();
    }

    @Override
    public String getQualifiedName() {
        return javaParserTypeAdapter.getQualifiedName();
    }

    @Override
    public List<ResolvedTypeParameterDeclaration> getTypeParameters() {
        return this.wrappedNode.getTypeParameters().stream()
                .map(tp -> new JavaParserTypeParameter(tp, typeSolver))
                .collect(toList());
    }

    @Override
    public List<ResolvedReferenceType> getInterfaces() {
        return wrappedNode.getImplementedTypes().stream()
                .map(this::toReferenceType)
                .collect(toList());
    }

    @Override
    public AccessSpecifier accessSpecifier() {
        return wrappedNode.getAccessSpecifier();
    }
}
