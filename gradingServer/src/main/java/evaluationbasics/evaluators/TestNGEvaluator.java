package evaluationbasics.evaluators;

import evaluationbasics.compilation.CompilationBox;
import evaluationbasics.reports.DiagnostedTest;
import evaluationbasics.exceptions.EmptyCodeException;
import evaluationbasics.exceptions.WrongNumberOfProvidedJavaElementsException;
import evaluationbasics.exceptions.ERROR_CODE;
import evaluationbasics.security.SwitchableSecurityManager;
import evaluationbasics.utils.SysOutGrabber;
import evaluationbasics.xml.*;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.internal.SystemProperty;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static evaluationbasics.xml.XMLParser.parseParameterGroups;

/**
 * Created by ilias on 26.08.16.
 */
public class TestNGEvaluator {

    private static final int TIMEOUT = 20000;
    private XMLConstructor xml;

    private TestNGEvaluator(XMLConstructor response) {
        this.xml = response;
    }

    public static void main(String... args) {
        SwitchableSecurityManager ssm = new SwitchableSecurityManager(1234, false);
        System.setSecurityManager(ssm);
        try {
            ObjectInputStream ois = new ObjectInputStream(System.in);
            ObjectOutputStream oos = new ObjectOutputStream(System.out);
            try {
                Element request = (Element) ois.readObject();
                Document response = processRequest(request);
                oos.writeObject(response);
                oos.flush();
            } catch (ClassNotFoundException e) {
            } finally {
            }
        } catch (IOException e) {
        } finally {
        }
    }

    /** @deprecated This method should only be used for debugging but not in the productive system. */
    public static Document processRequestInMainThread(Element request) {
        SwitchableSecurityManager ssm = new SwitchableSecurityManager(1234, false);
        System.setSecurityManager(ssm);
        return processRequest(request);
    }

    public static Document processRequest(Element request) {
        XMLConstructor response = new XMLConstructor();
        TestNGEvaluator eval = new TestNGEvaluator(response);
        eval.dispatchEvaluation(request);
        return response.getDocument();
    }

    /**
     * Function handles the original question type. The passed functions are wrapped in a class and then compared if at
     * least two are given.
     *
     * @param request xml Root element of the request
     * @return The respose xml document containing the evaluation.
     */
    private void dispatchEvaluation(Element request) {
        Element eAction = request.getChild("action");
        String actionRequested = eAction.getValue().toLowerCase();
        switch (actionRequested) {
            case "compiletestng":
                complationTest(request, "");
                break;

            case "compilestudenttestng":
                complationTest(request, "");
                break;

            case "runtestng":
                runTests(request);
                break;

            case "runstudenttestng":
                runStudentTests(request);
                break;

            case "feedbackstudenttestng":
                runTests(request);
                break;

            default:
                xml.error(ERROR_CODE.ACTION_NOT_KNOWN);
        }
    }

    private void runTests(Element request) {
        try {
            List<TestData> tests = XMLParser.parseTests(request);
            for (TestData test : tests) {
                DiagnostedTest dc = complationTest(request, test.name);
                if (dc != null && dc.isValidClass()) {
                    SysOutGrabber grabber = new SysOutGrabber();
                    EvaluationHelper.runInstanceMethod(dc.getTestSuiteClass(), "RunTests", new Object[]{test});
                    grabber.detach();
                    test.consoleOutput = grabber.getOutput();
                }
            }
            xml.responseToRunTest(tests);
        } catch (TimeoutException e) {
            xml.error("The execution took too long: " + e);
        } catch (IOException e) {
            xml.error("IOException "+ e);
        } catch (ClassNotFoundException e) {
            xml.error("ClassNotFoundException "+ e);
        } catch (org.jdom2.DataConversionException e) {
            xml.error("Found wrong datatype in xml: " + e);
        } catch (IllegalAccessException e) {
            xml.error("The method RunTests was not accessible." + e);
        } catch (InstantiationException e) {
            xml.error("Class Initialization error:" + e);
        } catch (WrongNumberOfProvidedJavaElementsException e) {
            xml.error(e.getMessage());
        } catch (InvocationTargetException e) {
            String stackTrace1 = "";
            for (StackTraceElement s : e.getStackTrace()) stackTrace1 += s.toString() + "\n";
            String stackTrace2 = "";
            for (StackTraceElement s : e.getCause().getStackTrace()) stackTrace2 += s.toString() + "\n";
            xml.error("Target invocation error: " + e.getMessage() + "\n" + stackTrace1);
            xml.error("Target invocation error cause: " + e.getCause() + "\n" + stackTrace2);
        } catch ( Exception e) {
            System.out.println("ERROR: "+e);
            xml.error("Unkown exception: " + e.getMessage() + "\n" + e.getStackTrace()[1]);
        }
    }

    private void runStudentTests(Element request) {
        try {
            Element solutionXML = request.getChild("solution");
            List<ParamGroup> groups = parseParameterGroups(solutionXML);

            DiagnostedTest dc = complationTest(request, "");
            if (dc != null && dc.isValidClass()) {
                for (ParamGroup group : groups) {
                    for (Params param : group.params) {
                        String[] args = new String[param.values.length];
                        for (int i = 0; i < param.values.length; ++i) {
                            args[i] = (String) param.values[i];
                        }

                        SysOutGrabber grabber = new SysOutGrabber();
                        param.zReturn = EvaluationHelper.runMainMethodWithParams(dc, args);
                        param.consoleOutput = grabber.getOutput();
                        grabber.detach();
                    }
                }
                xml.responseToRunStudentTest(groups);
            }
        } catch (TimeoutException e) {
            xml.error("The execution took too long: " + e);
        } catch (IOException e) {

        } catch (ClassNotFoundException e) {

        } catch (NoSuchMethodException e) {
            xml.error("MainMethodNotFound");
        } catch (org.jdom2.DataConversionException e) {
            xml.error("Found wrong datatype in xml: " + e);
        } catch (IllegalAccessException e) {
            xml.error("MainMethodNotFound");
        } catch (InvocationTargetException e) {
            xml.errorInvocationTargetException(e);
        }
    }


    private DiagnostedTest complationTest(Element request, String testName) {
        DiagnostedTest dc = null;
        try {

            Element solutionXML = request.getChild("solution");
            Element testXML = request.getChild("testgroup");

            String solutionCode = XMLParser.getCode(solutionXML);
            String testCode = XMLParser.getCode(testXML);

            dc = compileTest(solutionCode, testCode, testName);
            if (dc != null) {
                xml.responseToCompileTest(dc);
            }
            return dc;

        } catch (EmptyCodeException e) {
            xml.error("Provided code was empty: \n" + e);
        }
        return dc;
    }

    private DiagnostedTest compileTest(String solution, String test, String testName) {
        DiagnostedTest dc = null;
        try {
            CompilationBox cb = new CompilationBox();
            dc = cb.compileClassWithTest(solution, test, testName);
        } catch (WrongNumberOfProvidedJavaElementsException e) {
            xml.error(e);
        } catch (ClassNotFoundException e) {
            xml.error(e.toString());
        }
        return dc;
    }

}
