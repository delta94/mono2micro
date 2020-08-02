package collectors;

import spoon.reflect.code.*;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.cu.position.NoSourcePosition;
import spoon.reflect.declaration.*;
import spoon.reflect.path.CtRole;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.CtScanner;
import spoon.support.reflect.code.CtInvocationImpl;
import spoon.support.reflect.code.CtReturnImpl;
import util.Constants;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Stack;

public class FenixFrameworkCollector extends SpoonCollector {

    public FenixFrameworkCollector(int launcherChoice, String repoName, String projectPath)
            throws IOException {
        super(launcherChoice, repoName, projectPath);

        switch (launcherChoice) {
            case Constants.LAUNCHER:
            case Constants.MAVEN_LAUNCHER:
            case Constants.JAR_LAUNCHER:
                launcher.getEnvironment().setSourceClasspath(new String[]{
                        "./lib/fenix-framework-core-2.0.jar",
                        "./lib/spring-context-5.2.3.RELEASE.jar",
                        "./lib/bennu-core-6.6.0.jar"}
                );
                break;
            default:
                System.exit(1);
                break;
        }

        System.out.println("Generating AST...");
        launcher.buildModel();
    }

    @Override
    public void collectControllersAndEntities() {
        for(CtType<?> clazz : factory.Class().getAll()) {
            List<CtAnnotation<? extends Annotation>> annotations = clazz.getAnnotations();

            if (existsAnnotation(annotations, "Controller") ||
                existsAnnotation(annotations, "RestController") ||
                existsAnnotation(annotations, "RequestMapping") ||
                existsAnnotation(annotations, "GetMapping") ||
                existsAnnotation(annotations, "PostMapping") ||
                existsAnnotation(annotations, "PatchMapping") ||
                existsAnnotation(annotations, "PutMapping") ||
                existsAnnotation(annotations, "DeleteMapping") ||
                clazz.getSimpleName().endsWith("Controller") ||
                (clazz.getSuperclass() != null && clazz.getSuperclass().getSimpleName().equals("FenixDispatchAction"))
            ) {
                if (clazz instanceof CtClass) {
                    controllers.add((CtClass) clazz);
                }
            } else {
                CtTypeReference<?> superclassReference = clazz.getSuperclass();
                if (superclassReference != null && superclassReference.getSimpleName().endsWith("_Base")) {
                    allDomainEntities.add(clazz.getSimpleName());
                }
                if (!clazz.getSimpleName().endsWith("_Base") && (clazz.isAbstract() || clazz.isInterface()))
                    abstractClassesAndInterfacesImplementorsMap.put(clazz.getSimpleName(), new ArrayList<>());
            }
            allEntities.add(clazz.getSimpleName());
        }

        // 2nd iteration, to retrieve interface and abstract classes implementors and subclasses
        for(CtType<?> clazz : factory.Class().getAll()) {
            Set<CtTypeReference<?>> superInterfaces = clazz.getSuperInterfaces();
            for (CtTypeReference ctTypeReference : superInterfaces) {
                List<CtType> ctTypes = abstractClassesAndInterfacesImplementorsMap.get(ctTypeReference.getSimpleName());
                if (ctTypes != null) { // key exists
                    ctTypes.add(clazz);
                }
            }

            if (clazz.getSuperclass() != null) {
                // two times because, the first time we get the Base class and then the Domain Class
                CtTypeReference<?> superclass2 = clazz.getSuperclass().getSuperclass();
                if (superclass2 != null) {
                    List<CtType> ctTypes = abstractClassesAndInterfacesImplementorsMap.get(superclass2.getSimpleName());
                    if (ctTypes != null) // key exists
                        ctTypes.add(clazz);
                }
            }
        }
    }

    @Override
    public void methodCallDFS(
            CtExecutable callerMethod,
            CtAbstractInvocation prevCalleeLocation,
            Stack<SourcePosition> methodStack,
            Stack<Container<MySourcePosition>> toReturnNodeIdStack,
            Container<MySourcePosition> idPrev) {
        methodStack.push(callerMethod.getPosition());
        Container<MySourcePosition> id = new Container<>(idPrev);
        callerMethod.accept(new CtScanner() {
            private boolean lastStatementWasReturnValue;
            private Stack<Container<MySourcePosition>> branchOriginNodeId = new Stack<>();
            private Stack<Boolean> lastStatementWasReturnStack = new Stack<>();
            private Stack<Node> afterNode = new Stack<>();

            private <T> void visitCtAbstractInvocation(CtAbstractInvocation calleeLocation) {
                try {
                    if (calleeLocation == null)
                        return;

                    try {
                        CtMethod eDM = (CtMethod) calleeLocation.getExecutable().getExecutableDeclaration();
                        if (eDM.isAbstract()) {
                            // call to an abstract method
                            // lets replace this call with a call to every possible implementation of this
                            // method to consider all possible paths in our call graph
                            List<CtMethod> explicitImplementationsOfAbstractMethod = getExplicitImplementationsOfAbstractMethod(eDM);
                            Container<MySourcePosition> originCurrent = new Container<>(currentParentNodeId);
                            for (CtMethod ctMethod : explicitImplementationsOfAbstractMethod) {
                                currentParentNodeId = originCurrent;
                                if (!methodStack.contains(ctMethod.getPosition())) {
                                    methodCallDFS(ctMethod, calleeLocation, methodStack, toReturnNodeIdStack, id);
                                }
                            }
                            return;
                        }
                    } catch (Exception ignored) {}

                    if (calleeLocation.getExecutable().getDeclaringType().getSimpleName().endsWith("_Base")) {
                        registerBaseClass(calleeLocation.getExecutable().getExecutableDeclaration(), calleeLocation);
                    }
                    else if (calleeLocation.getExecutable().getDeclaringType().getSimpleName().equals("FenixFramework") &&
                            calleeLocation.getExecutable().getSimpleName().equals("getDomainObject")) {
                        registerDomainObject(calleeLocation);
                    }
                    else if (allEntities.contains(calleeLocation.getExecutable().getDeclaringType().getSimpleName())) {
                        if (!methodStack.contains(calleeLocation.getExecutable().getExecutableDeclaration().getPosition())) {
                            methodCallDFS(calleeLocation.getExecutable().getExecutableDeclaration(), calleeLocation, methodStack, toReturnNodeIdStack, id);
                        }
                    }
                } catch (Exception e) {
                    // null pointer exception or cast error, proceed
                }
            }

            @Override
            public <S> void visitCtSwitch(CtSwitch<S> switchStatement) {
                id.add(new MySourcePosition(switchStatement.getPosition(), true, false, switchStatement.toString()+"END"));
                afterNode.push(createGraphNode(id));
                super.visitCtSwitch(switchStatement);
                currentParentNodeId = afterNode.pop().getId();
            }

            @Override
            public <T, S> void visitCtSwitchExpression(CtSwitchExpression<T, S> switchExpression) {
                id.add(new MySourcePosition(switchExpression.getPosition(), true, false, switchExpression.toString()+"END"));
                afterNode.push(createGraphNode(id));
                super.visitCtSwitchExpression(switchExpression);
                currentParentNodeId = afterNode.pop().getId();
            }

            @Override
            public <R> void visitCtReturn(CtReturn<R> returnStatement) {
                super.visitCtReturn(returnStatement);
                lastStatementWasReturnStack.pop();
                lastStatementWasReturnStack.push(true);
            }

            @Override
            public <R> void visitCtBlock(CtBlock<R> block) {
                id.add(new MySourcePosition(block.getPosition(), true, false, block.toString()+"END"));
                afterNode.push(createGraphNode(id));
                lastStatementWasReturnStack.push(false);
                super.visitCtBlock(block);
                lastStatementWasReturnValue = lastStatementWasReturnStack.pop();
                Node pop = afterNode.pop();
                linkNodes(currentParentNodeId, pop.getId());
                currentParentNodeId = pop.getId();
            }

            @Override
            public void scan(CtRole role, CtElement element) {
                // pre stuff before scan
                switch (role) {
                    case THEN:
                        branchOriginNodeId.push(currentParentNodeId); // then
                        branchOriginNodeId.push(currentParentNodeId); // else
                        break;
                    case ELSE:
                        break;
                    case CASE:
                        List<CtCase> cases = null;
                        if (element.getParent() instanceof  CtSwitch)
                            cases = ((CtSwitch) element.getParent()).getCases();
                        else if (element.getParent() instanceof  CtSwitchExpression)
                            cases = ((CtSwitchExpression) element.getParent()).getCases();

                        if (cases.get(0).equals(element)) {
                            // the first case will populate the origin nodes stack
                            int size = cases.size();
                            for (int i = 0 ; i < size; i++)
                                branchOriginNodeId.push(currentParentNodeId);
                        }
                        // case statements are not considered to have blocks inside (although they can return)
                        lastStatementWasReturnStack.push(false);
                        break;
                    case BODY:
                        branchOriginNodeId.push(currentParentNodeId);
                        if (element == null) // lambda
                            break;
                        CtElement parent = element.getParent();

                        if (parent instanceof CtFor || parent instanceof CtWhile || parent instanceof CtForEach ||
                        parent instanceof CtLambda) {
                            // flow where for is not executed
                            linkNodes(currentParentNodeId, afterNode.peek().getId());
                        }
                        else if (parent instanceof CtTry || parent instanceof CtTryWithResource) {
                            for (CtCatch ctCatch : ((CtTry) parent).getCatchers()) {
                                branchOriginNodeId.push(currentParentNodeId);
                            }
                        }
                        break;
                }

                Node elementNode = null;
                switch (role) {
                    case THEN:
                    case ELSE:
                    case CASE:
                    case CATCH:
                    case BODY:
                        if (element != null) {
                            id.add(new MySourcePosition(element.getPosition(), false, false, element.toString()));
                            elementNode = createGraphNode(id);

                            // corner case of cases without breaks/returns
                            if (role.equals(CtRole.CASE) && element.getParent() instanceof CtSwitch) {
                                // if the previous case didn't end in break/return, we have to link it to the current case
                                List<CtCase> cases = ((CtSwitch) element.getParent()).getCases();
                                for (int i = 0; i < cases.size(); i++) {
                                    CtCase ctCase = cases.get(i);
                                    if (ctCase.equals(element)) {
                                        if (i != 0) {
                                            CtCase previousCase = cases.get(i - 1);
                                            List<CtStatement> previousCaseStmts = previousCase.getStatements();
                                            CtStatement lastStmt = previousCaseStmts.get(previousCaseStmts.size() - 1);
                                            if (!(lastStmt instanceof CtBreak) && !(lastStmt instanceof CtReturn)) {
                                                // the end of the case inspection link the switch case to the afterSwitchNode
                                                // by default, so we have to unlink it, and relink to the next case
                                                unlinkLast(currentParentNodeId);
                                                // link end of previous case with current case
                                                linkNodes(currentParentNodeId, elementNode.getId());
                                            }
                                        }
                                        break;
                                    }
                                }
                            }

                            currentParentNodeId = elementNode.getId();
                            linkNodes(branchOriginNodeId.pop(), elementNode.getId());
                        }
                        else { // null else case
                            // if there is no else, we can skip if and go to post-if (afterNode)
                            linkNodes(branchOriginNodeId.pop(), afterNode.peek().getId());
                        }
                        break;
                }

                super.scan(role, element);

                // pre stuff after scan
                switch (role) {
                    case CASE:
                        // case statements are not considered to have blocks inside (although they can return)
                        lastStatementWasReturnValue = lastStatementWasReturnStack.pop();
                        break;
                }

                switch (role) {
                    case THEN:
                    case ELSE:
                    case CASE:
                    case BODY:
                        if (elementNode != null) {
                            if (!lastStatementWasReturnValue) {
                                if (!afterNode.isEmpty()) {
                                    linkNodes(currentParentNodeId, afterNode.peek().getId());
                                }
                                else {
                                    // estamos no fim do metodo e nao há next, temos que voltar para o metodo anterior
                                    if (!toReturnNodeIdStack.isEmpty()) {
                                        Container<MySourcePosition> peekId = toReturnNodeIdStack.peek();
                                        linkNodes(currentParentNodeId, peekId);
                                    }
                                }
                            }
                            else {
                                // branch that ended in return
                                // must be linked to the end of the method execution
                                if (!toReturnNodeIdStack.isEmpty()) {
                                    Container<MySourcePosition> peekId = toReturnNodeIdStack.peek();
                                    linkNodes(currentParentNodeId, peekId);
                                }
                            }
                        }
                        break;
                }
            }

            @Override
            public void visitCtFor(CtFor forLoop) {
                id.add(new MySourcePosition(forLoop.getPosition(), true, false, forLoop.toString()+"END"));
                afterNode.push(createGraphNode(id));
                super.visitCtFor(forLoop);
                currentParentNodeId = afterNode.pop().getId();
            }

            @Override
            public void visitCtForEach(CtForEach foreach) {
                id.add(new MySourcePosition(foreach.getPosition(), true, false, foreach.toString()+"END"));
                afterNode.push(createGraphNode(id));
                super.visitCtForEach(foreach);
                currentParentNodeId = afterNode.pop().getId();
            }

            @Override
            public void visitCtIf(CtIf ifElement) {
                id.add(new MySourcePosition(ifElement.getPosition(), true, false, ifElement.toString()+"END"));
                afterNode.push(createGraphNode(id));
                super.visitCtIf(ifElement);
                currentParentNodeId = afterNode.pop().getId();
            }

            @Override
            public void visitCtAnonymousExecutable(CtAnonymousExecutable anonymousExec) {
                System.err.println("visitCtAnonymousExecutable");
                System.exit(1);
                super.visitCtAnonymousExecutable(anonymousExec);
            }

            @Override
            public <T> void visitCtConditional(CtConditional<T> conditional) {
                id.add(new MySourcePosition(conditional.getPosition(), true, false, conditional.toString()+"END"));
                afterNode.push(createGraphNode(id));
                super.visitCtConditional(conditional);
                currentParentNodeId = afterNode.pop().getId();
            }

            @Override
            public void visitCtWhile(CtWhile whileLoop) {
                id.add(new MySourcePosition(whileLoop.getPosition(), true, false, whileLoop.toString()+"END"));
                afterNode.push(createGraphNode(id));
                super.visitCtWhile(whileLoop);
                currentParentNodeId = afterNode.pop().getId();
            }

            @Override
            public void visitCtTryWithResource(CtTryWithResource tryWithResource) {
                id.add(new MySourcePosition(tryWithResource.getPosition(), true, false, tryWithResource.toString()+"END"));
                afterNode.push(createGraphNode(id));
                super.visitCtTryWithResource(tryWithResource);
                currentParentNodeId = afterNode.pop().getId();
            }

            @Override
            public void visitCtTry(CtTry tryBlock) {
                id.add(new MySourcePosition(tryBlock.getPosition(), true, false, tryBlock.toString()+"END"));
                afterNode.push(createGraphNode(id));
                super.visitCtTry(tryBlock);
                currentParentNodeId = afterNode.pop().getId();
            }

            @Override
            public <T> void visitCtLambda(CtLambda<T> lambda) {
                id.add(new MySourcePosition(lambda.getPosition(), true, false, lambda.toString()+"END"));
                afterNode.push(createGraphNode(id));
                // lambda have a surrounding context, such as methods
                toReturnNodeIdStack.push(afterNode.peek().getId());
                Container<MySourcePosition> currentBefore = new Container<>(currentParentNodeId);
                super.visitCtLambda(lambda);

                if (lambda.getBody() == null) {
                    // No block means CtBlock wasn't visited and the end of the expression won't be connected
                    // to any node. We have to manually link it to the node next to the the lambda expression
                    Container<MySourcePosition> peekId = toReturnNodeIdStack.peek();
                    linkNodes(currentParentNodeId, peekId);
                }

                toReturnNodeIdStack.pop();

                currentParentNodeId = afterNode.pop().getId();
            }

            @Override
            public <T> void visitCtInvocation(CtInvocation<T> invocation) {
                SourcePosition position = invocation.getPosition();
                boolean isSuper = false;
                if (position instanceof NoSourcePosition) {
                    if (invocation.toString().equals("super()")) {
                        position = invocation.getParent().getPosition();
                        isSuper = true;
                    }
                    else {
                        System.err.println("Invocation.getPosition = NoSourcePosition and call != super()");
                        System.exit(1);
                    }
                }
                id.add(new MySourcePosition(position, true, isSuper, invocation.toString()+"END"));
                afterNode.push(createGraphNode(id));
                toReturnNodeIdStack.push(afterNode.peek().getId());
                super.visitCtInvocation(invocation);
                postVisitCtAbstractInvocation(invocation);
            }

            @Override
            public <T> void visitCtConstructorCall(CtConstructorCall<T> ctConstructorCall) {
                SourcePosition position = ctConstructorCall.getPosition();
                if (position instanceof NoSourcePosition) {
                    System.err.println("NoSourcePosition unexpected");
                    System.exit(1);
                }
                id.add(new MySourcePosition(position, true, false, ctConstructorCall.toString()+"END"));
                afterNode.push(createGraphNode(id));
                toReturnNodeIdStack.push(afterNode.peek().getId());
                super.visitCtConstructorCall(ctConstructorCall);
                postVisitCtAbstractInvocation(ctConstructorCall);
            }

            private <T> void postVisitCtAbstractInvocation(CtAbstractInvocation<T> invocation) {
                Container<MySourcePosition> currentBefore = new Container<>(currentParentNodeId);
                visitCtAbstractInvocation(invocation);

                toReturnNodeIdStack.pop();

                Node pop = afterNode.pop();
                if (currentBefore.equals(currentParentNodeId)) {
                    // visited method not explicitly implemented, block element was not visited
                    // thus we have to manually link the node which made
                    // the invocation with the after invocation node
                    linkNodes(currentParentNodeId, pop.getId());
                    currentParentNodeId = pop.getId();
                }
                else
                    currentParentNodeId = pop.getId();
            }
        });

        methodStack.pop();
    }

    private List<CtMethod> getExplicitImplementationsOfAbstractMethod(CtMethod abstractMethod) {
        List<CtMethod> explicitMethods = new ArrayList<>();
        CtType<?> declaringType = abstractMethod.getDeclaringType();
        List<CtType> implementors = abstractClassesAndInterfacesImplementorsMap.get(declaringType.getSimpleName());
        if (implementors != null) {
            for (CtType ctImplementorType : implementors) {
                List<CtMethod> methodsByName = ctImplementorType.getMethodsByName(abstractMethod.getSimpleName());
                if (methodsByName.size() > 0) {
                    for (CtMethod ctM : methodsByName) {
                        if (isEqualMethodSignature(abstractMethod, ctM)) {
                            explicitMethods.add(ctM);
                        }
                    }
                }
            }
        }

        return explicitMethods;
    }

    private boolean isEqualMethodSignature(CtMethod method1, CtMethod method2) {
        // compare return type
        if (method1.getType().equals(method2.getType())) {
            // compare parameters type
            List ctMParams1 = method1.getParameters();
            List ctMParams2 = method2.getParameters();
            if (ctMParams1.size() != ctMParams2.size())
                return false;
            for (int i = 0; i < ctMParams1.size(); i++) {
                CtParameter ctMParam1 = (CtParameter) ctMParams1.get(i);
                CtParameter ctMParam2 = (CtParameter) ctMParams2.get(i);
                if (!ctMParam1.getType().equals(ctMParam2.getType())) {
                    return false;
                }
            }
        }
        return true;
    }

    private void registerBaseClass(CtExecutable callee, CtAbstractInvocation calleeLocation) {
        String methodName = callee.getSimpleName();
        String mode = "";
        String returnType = "";
        List<String> argTypes = new ArrayList<>();
        if (methodName.startsWith("get")) {
            mode = "R";
            CtTypeReference returnTypeReference = callee.getType();
            if (returnTypeReference.isParameterized())
                returnType = returnTypeReference.getActualTypeArguments().get(0).getSimpleName();
            else
                returnType = returnTypeReference.getSimpleName();
        }
        else if (methodName.startsWith("set") || methodName.startsWith("add") || methodName.startsWith("remove")) {
            mode = "W";
            List<CtParameter> calleeParameters = callee.getParameters();
            for (CtParameter ctP : calleeParameters) {
                CtTypeReference parameterType = ctP.getType();
                if (parameterType.isParameterized()) {
                    for (CtTypeReference ctTR : parameterType.getActualTypeArguments())
                        argTypes.add(ctTR.getSimpleName());
                }
                else
                    argTypes.add(ctP.getType().getSimpleName());
            }
        }

        String baseClassName = callee.getParent(CtClass.class).getSimpleName();
        baseClassName = baseClassName.substring(0, baseClassName.length()-5); //remove _Base

        String resolvedType = " ";
        resolvedType = resolveTypeBase(calleeLocation);

        if (mode.equals("R")) {
            // Class Read
            if (allDomainEntities.contains(resolvedType))
                addEntitiesSequenceAccess(resolvedType, mode);
            else if (allDomainEntities.contains(baseClassName))
                addEntitiesSequenceAccess(baseClassName, mode);

            // Return Type Read
            if (allDomainEntities.contains(returnType))
                addEntitiesSequenceAccess(returnType, mode);
        }
        else if (mode.equals("W")) {
            // Class Read
            if (allDomainEntities.contains(resolvedType))
                addEntitiesSequenceAccess(resolvedType, mode);
            else if (allDomainEntities.contains(baseClassName))
                addEntitiesSequenceAccess(baseClassName, mode);

            // Argument Types Read
            for (String type : argTypes) {
                if (allDomainEntities.contains(type))
                    addEntitiesSequenceAccess(type, mode);
            }
        }
    }

    private String resolveTypeBase(CtAbstractInvocation calleeLocation) {
        CtExpression target = ((CtInvocationImpl) calleeLocation).getTarget();
        if (target.getTypeCasts().size() > 0)
            return ((CtTypeReference) target.getTypeCasts().get(0)).getSimpleName();
        else
            return target.getType().getSimpleName();
    }

    private void registerDomainObject(CtAbstractInvocation abstractCalleeLocation) throws Exception {
        // Example: final CurricularCourse course = FenixFramework.getDomainObject(values[0]);
        // Example: return (DepartmentUnit) FenixFramework.getDomainObject(this.getSelectedDepartmentUnitID());
        CtInvocation calleeLocation = (CtInvocation) abstractCalleeLocation;
        String resolvedType;
        resolvedType = resolveTypeDomainObject(calleeLocation);

        if (allDomainEntities.contains(resolvedType)) {
            addEntitiesSequenceAccess(resolvedType, "R");
        }
    }

    private String resolveTypeDomainObject(CtInvocation calleeLocation) throws Exception {
        CtElement parent = calleeLocation.getParent();
        if (calleeLocation.getTypeCasts().size() > 0)
            return ((CtTypeReference) calleeLocation.getTypeCasts().get(0)).getSimpleName();
        else if (parent instanceof CtReturnImpl)
            return parent.getParent(CtMethod.class).getType().getSimpleName();
        else if (parent instanceof CtLocalVariable)
            return ((CtLocalVariable) parent).getType().getSimpleName();
        else if (parent instanceof CtAssignment)
            return ((CtAssignment) parent).getAssigned().getType().getSimpleName();
        else
            throw new Exception("Couldn't Resolve Type.");
    }
}
