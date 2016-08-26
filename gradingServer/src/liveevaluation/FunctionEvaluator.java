package liveevaluation;

import evaluationbasics.DiagnostedMethodClass;
import evaluationbasics.EmptyCodeException;
import evaluationbasics.CompilationBox;
import evaluationbasics.TooManyMethodsException;
import javafx.util.Pair;
import org.jdom2.Element;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import static liveevaluation.XMLParser.*;

/**
 * Created by ilias on 26.08.16.
 */
public class FunctionEvaluator {

    public static void eval(Element request, XMLConstructor response) {
        FunctionEvaluator eval = new FunctionEvaluator(response);
        eval.dispatchFunctionAction(request);
    }

    private XMLConstructor xml;

    private FunctionEvaluator(XMLConstructor response) {
        this.xml = response;
    }

    /**
     * Function handles the original question type. The passed functions are wrapped in a class and then compared if at
     * least two are given.
     *
     * @param request XML Root element of the request
     * @return The respose xml document containing the evaluation.
     */
    protected void dispatchFunctionAction(Element request) {
        Element eAction = request.getChild("action");
        String actionRequested = eAction.getValue().toLowerCase();
        switch (actionRequested) {
            case "compile-teacher":
                compileMethod(request, "teacher");
                break;

            case "compile-student":
                compileMethod(request, "student");
                break;

            case "run-teacher":
                runMethodOnParams(request, "teacher");
                break;

            case "run-student":
                runMethodOnParams(request, "student");
                break;

            case "compare":
                compareMethods(request, "student", "teacher");
                break;

            default:
                xml.error(ERROR_CODE.ACTION_NOT_KNOWN);
        }
    }

    protected void compileMethod(Element request, String person) {
        try{
            Element codeOwner = request.getChild(person);

            Pair<DiagnostedMethodClass,CodeUnit> smethod = compileMethod(codeOwner);
            List<ParamGroup> sparams = parseParameterGroups(codeOwner);

            runMethodOnParams(smethod.getKey(),sparams);

            xml.compileMethodResponse(sparams,smethod.getValue());

        } catch ( EmptyCodeException e) {
            xml.error("Provided code was empty: "+e);
        } catch ( TooManyMethodsException e ) {
            xml.error("Too many methods provided: "+e);
        } catch ( org.jdom2.DataConversionException e) {
            xml.error("Found wrong datatype in XML: "+e);
        } catch ( Exception e) {
            xml.errorUnknown(e);
        }
    }

    protected void runMethodOnParams(Element request, String person) {
        try {
            Element element = request.getChild(person);

            Pair<DiagnostedMethodClass, CodeUnit> method = compileMethod(element);
            List<ParamGroup> params = parseParameterGroups(element);

            runMethodOnParams(method.getKey(), params);

            xml.runMethodResponse(params, method.getValue());

        } catch ( EmptyCodeException e) {
            xml.error("Provided code was empty: "+e);
        } catch ( TooManyMethodsException e ) {
            xml.error("Too many methods provided: "+e);
        } catch ( org.jdom2.DataConversionException e) {
            xml.error("Found wrong datatype in XML: "+e);
        } catch ( Exception e) {
            xml.errorUnknown(e);
        }
    }

    protected void compareMethods(Element request, String testee, String examinator) {
        try {
            Element testeeXML = request.getChild(testee);
            Element examinatorXML = request.getChild(examinator);
            Pair<DiagnostedMethodClass, CodeUnit> testeeMethod = compileMethod(testeeXML);
            Pair<DiagnostedMethodClass, CodeUnit> examinatorMethod = compileMethod(examinatorXML);

            List<ParamGroup> parameters = parseParameterGroups(request.getChild(examinator));

            Pair<DiagnostedMethodClass, CodeUnit>[] methodArray = new Pair[]{testeeMethod, examinatorMethod};
            Vector<Pair<DiagnostedMethodClass, CodeUnit>> methods = new Vector(Arrays.asList(methodArray));

            List<List<ParamGroup>> results = runAllMethodsOnParams(methods, parameters);

            List<ParamGroup> testeeResults = results.get(0);
            List<ParamGroup> examinatorResults = results.get(1);

            compareParamGroupLists(testeeResults, examinatorResults);

            xml.compareMethodResponse(testeeResults, testeeMethod.getValue());

        } catch ( EmptyCodeException e) {
            xml.error("Provided code was empty: "+e);
        } catch ( TooManyMethodsException e ) {
            xml.error("Too many methods provided: "+e);
        } catch ( org.jdom2.DataConversionException e) {
            xml.error("Found wrong datatype in XML: "+e);
        } catch ( Exception e) {
            xml.errorUnknown(e);
        }
    }


    protected Pair<DiagnostedMethodClass, CodeUnit> compileMethod(Element codeOwner) throws EmptyCodeException, org.jdom2.DataConversionException, TooManyMethodsException, ClassNotFoundException {
        Pair<DiagnostedMethodClass, CodeUnit> result;
        boolean checkType = false;
        int id = -1;
        String methodCode = "";

        Element code = codeOwner.getChild("code");
        checkType = code.getAttribute("checkmethodtype").getBooleanValue();
        id = code.getAttribute("id").getIntValue();
        methodCode = code.getValue();

        if (methodCode == null || methodCode.isEmpty() || methodCode == "") {
            throw new EmptyCodeException("Code was empty.");
        } else {
            return compileMethod(methodCode, id, checkType);
        }
    }

    protected Pair<DiagnostedMethodClass, CodeUnit> compileMethod(String methodCode, int id, boolean checkType) throws TooManyMethodsException, ClassNotFoundException {
        CodeUnit codeUnit = new CodeUnit();
        DiagnostedMethodClass diagnostedMethodClass = null;
        CompilationBox cp = new CompilationBox();

        diagnostedMethodClass = cp.compileMethod(methodCode);
        codeUnit.compileable = diagnostedMethodClass.isValidClass();
        codeUnit.diagnostics = diagnostedMethodClass.getDiagnostic();
        codeUnit.id = id;
        codeUnit.lineOffset = diagnostedMethodClass.getOffset();

        if (codeUnit.compileable && checkType && diagnostedMethodClass != null) {
            codeUnit.methodtype = CompilationBox.getMethodType(diagnostedMethodClass);
        } else if ( checkType ){
            codeUnit.methodtypeError = "Could not infer method diagnostics.";
        }

        return new Pair(diagnostedMethodClass, codeUnit);
    }

    protected boolean compareParamGroupLists(List<ParamGroup> testee, List<ParamGroup> examinator) {
        assert (testee.size() == examinator.size());
        Iterator<ParamGroup> tIt = testee.iterator();
        Iterator<ParamGroup> eIt = examinator.iterator();

        boolean allEqual = true;

        while (tIt.hasNext() && eIt.hasNext()) {
            ParamGroup tGroup = tIt.next();
            ParamGroup eGroup = eIt.next();

            if (compareParamsLists(tGroup.params, eGroup.params)) {
                tGroup.equals = true;
                tGroup.reachedPoints = eGroup.points;
            } else {
                allEqual = false;
            }
        }
        return allEqual;
    }

    protected boolean compareParamsLists(List<Params> testee, List<Params> examinator) {
        assert (testee.size() == examinator.size());
        Iterator<Params> tIt = testee.iterator();
        Iterator<Params> eIt = examinator.iterator();

        boolean allEqual = true;

        while (tIt.hasNext() && eIt.hasNext()) {
            Params tParams = tIt.next();
            Params eParams = eIt.next();
            if (String.valueOf(tParams.zReturn).equals(String.valueOf(eParams.zReturn))) {
                tParams.equals = true;
            } else {
                allEqual = false;
            }
        }
        return allEqual;
    }

//    private Document compileClasses(Element request) {
//        Document returnDoc;
//        LinkedList<CodeUnit> codeUnits = new LinkedList<>();
//        for (Element codeunit : eCodeunits) {
//            CodeUnit tempCodeUnit = new CodeUnit();
//            String classCode = codeunit.getChild("code").getValue();
//            DiagnostedClass dcClass = CompilationBox.compileClass(classCode, CompilationBox.getClassName(classCode));
//            try {
//                tempCodeUnit.id = codeunit.getAttribute("id").getIntValue();
//            } catch (DataConversionException e) {
//                tempCodeUnit.id = -1;
//                e.printStackTrace();
//            }
//            tempCodeUnit.diagnostics = dcClass.getDiagnostic();
//            tempCodeUnit.compileable = dcClass.isValidClass();
//            codeUnits.add(tempCodeUnit);
//        }
//        returnDoc = XMLConstructor.xml("compileClass", codeUnits, null);
//        return returnDoc;
//    }


    protected final void runMethodOnParams(DiagnostedMethodClass dcMethod, List<ParamGroup> parameters) {
        for (ParamGroup group : parameters) {
            for (Params params : group.params) {
                try {
                    params.zReturn = EvaluationHelper.runMethodOnParams(dcMethod, params.values);
                } catch (Exception e) {
                    params.zReturn = null;
                    String error = e.getMessage();
                    if (error.equals("")) {
                        params.error = "Could not run method on the value: {" + Arrays.toString(params.values) + "}";
                    }
                    params.error = error;
                }
            }
        }
    }


    protected final List<List<ParamGroup>> runAllMethodsOnParams(List<Pair<DiagnostedMethodClass, CodeUnit>> methods, List<ParamGroup> parameters) {

        List<List<ParamGroup>> results = new Vector<List<ParamGroup>>();
        for (Pair<DiagnostedMethodClass, CodeUnit> m : methods) {
            DiagnostedMethodClass method = m.getKey();
            CodeUnit result = m.getValue();
            List<ParamGroup> p = new Vector(parameters);

            if (method.isValidClass() && CompilationBox.isVoid(method))
                result.error = "" + ERROR_CODE.FOUND_VOID_METHOD;

            runMethodOnParams(method, p);
            results.add(p);
        }
        return results;
    }

}
