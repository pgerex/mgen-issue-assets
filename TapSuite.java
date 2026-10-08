package org.so.example.mgen;

import org.so.example.mgen.reports.ApplicationEvaluations;
import org.so.example.mgen.reports.AutoReleasedFromQuarantineComponents;
import org.so.example.mgen.reports.AutoReleasedFromQuarantineConfig;
import org.so.example.mgen.reports.AutoReleasedFromQuarantineSummary;
import org.so.example.mgen.reports.PolicyViolations;
import org.so.example.mgen.reports.QuarantinedComponents;
import org.so.example.mgen.reports.QuarantinedComponentsSummary;
import org.so.example.mgen.reports.ReasonFormatter;
import org.so.example.mgen.reports.Waivers;
import org.so.example.mgen.service.CsvFileService;
import org.so.example.mgen.service.FileIoService;
import org.so.example.mgen.service.PolicyIdsService;
import org.so.example.mgen.util.CsvUtil;
import org.so.example.mgen.util.FilenameInfo;
import org.so.example.mgen.util.JsonValues;
import org.so.example.mgen.util.UtilService;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TapSuite {

    private static int total = 0;
    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("mgen-tap-");

        testCsvUtil();
        testCsvParsing();
        testJsonValues();
        testFileIoService(root);
        testReadCsvFile(root);
        testFilenameInfo();
        testUtilService();
        testPolicyIdsService();
        testReasonFormatter();
        testGetRows(root);
        testApplicationEvaluations(root);
        testWaivers(root);
        testPolicyViolations(root);
        testQuarantinedComponents(root);
        testAutoReleasedFromQuarantineComponents(root);
        testQuarantinedComponentsSummary(root);
        testAutoReleasedFromQuarantineSummary(root);
        testAutoReleasedFromQuarantineConfig(root);

        System.out.println("# pass " + passed);
        System.out.println("# fail " + failed);
        System.exit(failed == 0 ? 0 : 1);
    }

    private static void check(String name, boolean condition) {
        total++;
        if (condition) {
            passed++;
            System.out.println("ok " + total + " - " + name);
        }
        else {
            failed++;
            System.out.println("not ok " + total + " - " + name);
        }
    }

    private static void same(String name, String expected, String actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            System.out.println("# " + name + " expected <" + render(expected) + "> actual <" + render(actual) + ">");
        }
        check(name, expected == null ? actual == null : expected.equals(actual));
    }

    private static String render(String value) {
        if (value == null) {
            return "null";
        }
        return value.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static void expectIllegalArgumentException(String name, String message, Runnable action) {
        try {
            action.run();
            check(name, false);
        }
        catch (IllegalArgumentException e) {
            same(name, message, e.getMessage());
        }
        catch (RuntimeException e) {
            System.out.println("# " + name + " threw " + e);
            check(name, false);
        }
    }

    private static JsonObject parseObject(String json) {
        try (JsonReader reader = Json.createReader(new StringReader(json))) {
            return reader.readObject();
        }
    }

    private static JsonArray parseArray(String json) {
        try (JsonReader reader = Json.createReader(new StringReader(json))) {
            return reader.readArray();
        }
    }

    private static Path dir(Path root, String name) throws Exception {
        Path d = root.resolve(name);
        Files.createDirectories(d);
        return d;
    }

    private static FileIoService fio(Path d) {
        return new FileIoService(d.toString());
    }

    private static String content(Path d, String file) throws Exception {
        return new String(Files.readAllBytes(d.resolve(file)), StandardCharsets.UTF_8);
    }

    private static String viaObject(CsvFileService report, Path d, String file, String json) throws Exception {
        report.makeCsvFile(fio(d), parseObject(json));
        return content(d, file);
    }

    private static String viaReader(CsvFileService report, Path d, String file, String json) throws Exception {
        try (JsonReader reader = Json.createReader(new StringReader(json))) {
            report.makeCsvFile(fio(d), reader);
        }
        return content(d, file);
    }

    private static String join(String[] values) {
        return String.join("|", values);
    }

    private static List<String[]> rows(String[]... values) {
        return new ArrayList<>(Arrays.asList(values));
    }

    private static String renderRows(List<String[]> records) {
        List<String> rendered = new ArrayList<>();
        for (String[] record : records) {
            rendered.add(Arrays.toString(record));
        }
        return rendered.toString();
    }

    private static void sameRows(String name, List<String[]> expected, List<String[]> actual) {
        boolean matches = expected.size() == actual.size();
        if (matches) {
            for (int i = 0; i < expected.size(); i++) {
                if (!Arrays.equals(expected.get(i), actual.get(i))) {
                    matches = false;
                    break;
                }
            }
        }
        if (!matches) {
            System.out.println("# " + name + " expected " + renderRows(expected) + " actual " + renderRows(actual));
        }
        check(name, matches);
    }

    private static void testCsvUtil() {
        same("CsvUtil.escapeField(null) returns empty string", "", CsvUtil.escapeField(null));
        same("CsvUtil.escapeField(\"\") returns empty string", "", CsvUtil.escapeField(""));
        same("CsvUtil.escapeField leaves a plain value unchanged", "abc", CsvUtil.escapeField("abc"));
        same("CsvUtil.escapeField quotes a comma", "\"a,b\"", CsvUtil.escapeField("a,b"));
        same("CsvUtil.escapeField doubles embedded quotes", "\"a\"\"b\"", CsvUtil.escapeField("a\"b"));
        same("CsvUtil.escapeField quotes a LF", "\"a\nb\"", CsvUtil.escapeField("a\nb"));
        same("CsvUtil.escapeField quotes a CR", "\"a\rb\"", CsvUtil.escapeField("a\rb"));
        same("CsvUtil.escapeField quotes a leading space", "\" a\"", CsvUtil.escapeField(" a"));
        same("CsvUtil.escapeField quotes a trailing space", "\"a \"", CsvUtil.escapeField("a "));
        same("CsvUtil.escapeField quotes a leading tab", "\"\ta\"", CsvUtil.escapeField("\ta"));
        same("CsvUtil.escapeField leaves an interior space unquoted", "a b", CsvUtil.escapeField("a b"));
        same("CsvUtil.escapeField doubles every embedded quote", "\"a\"\"\"\"b\"", CsvUtil.escapeField("a\"\"b"));
        same("CsvUtil.formatRecord escapes fields and terminates with LF", "a,,\"b,c\"\n", CsvUtil.formatRecord("a", null, "b,c"));
        same("CsvUtil.formatRecord accepts a list", "x,y\n", CsvUtil.formatRecord(Arrays.asList("x", "y")));
        expectIllegalArgumentException("CsvUtil.formatRecord rejects null fields", "fields must not be null",
                () -> CsvUtil.formatRecord((String[]) null));
    }

    private static void testJsonValues() {
        JsonObject object = Json.createObjectBuilder()
                .add("text", "hello")
                .add("number", 42)
                .add("fraction", 7.9)
                .add("truth", true)
                .addNull("nil")
                .add("array", Json.createArrayBuilder().add("x").add("y"))
                .add("nested", Json.createObjectBuilder().add("k", "v"))
                .add("numericText", "12")
                .add("badText", "nope")
                .add("overflowText", "999999999999")
                .add("boolText", "TRUE")
                .add("badBoolText", "yes")
                .build();

        same("JsonValues.getString missing key returns empty", "", JsonValues.getString(object, "missing"));
        same("JsonValues.getString JSON null returns empty", "", JsonValues.getString(object, "nil"));
        same("JsonValues.getString returns the string", "hello", JsonValues.getString(object, "text"));
        same("JsonValues.getString renders a number", "42", JsonValues.getString(object, "number"));
        same("JsonValues.getString renders a boolean", "true", JsonValues.getString(object, "truth"));
        expectIllegalArgumentException("JsonValues.getString rejects an array", "field 'array' is not a scalar",
                () -> JsonValues.getString(object, "array"));
        expectIllegalArgumentException("JsonValues.getString rejects an object", "field 'nested' is not a scalar",
                () -> JsonValues.getString(object, "nested"));

        check("JsonValues.getInt missing key returns zero", JsonValues.getInt(object, "missing") == 0);
        check("JsonValues.getInt JSON null returns zero", JsonValues.getInt(object, "nil") == 0);
        check("JsonValues.getInt reads a number", JsonValues.getInt(object, "number") == 42);
        check("JsonValues.getInt truncates a fractional number", JsonValues.getInt(object, "fraction") == 7);
        check("JsonValues.getInt parses a numeric string", JsonValues.getInt(object, "numericText") == 12);
        expectIllegalArgumentException("JsonValues.getInt rejects a non-numeric string", "field 'badText' is not an integer",
                () -> JsonValues.getInt(object, "badText"));
        expectIllegalArgumentException("JsonValues.getInt rejects an overflowing string", "field 'overflowText' is not an integer",
                () -> JsonValues.getInt(object, "overflowText"));
        expectIllegalArgumentException("JsonValues.getInt rejects a boolean", "field 'truth' is not an integer",
                () -> JsonValues.getInt(object, "truth"));

        check("JsonValues.getBoolean missing key returns false", !JsonValues.getBoolean(object, "missing"));
        check("JsonValues.getBoolean reads true", JsonValues.getBoolean(object, "truth"));
        check("JsonValues.getBoolean reads a case-insensitive string", JsonValues.getBoolean(object, "boolText"));
        check("JsonValues.getBoolean reads false", !JsonValues.getBoolean(parseObject("{\"b\":false}"), "b"));
        expectIllegalArgumentException("JsonValues.getBoolean rejects a non-boolean string", "field 'badBoolText' is not a boolean",
                () -> JsonValues.getBoolean(object, "badBoolText"));

        check("JsonValues.getArray missing key returns an empty array", JsonValues.getArray(object, "missing").size() == 0);
        check("JsonValues.getArray JSON null returns an empty array", JsonValues.getArray(object, "nil").size() == 0);
        JsonArray values = JsonValues.getArray(object, "array");
        same("JsonValues.getArray returns the array", "x|y", values.getString(0) + "|" + values.getString(1));
        expectIllegalArgumentException("JsonValues.getArray rejects a scalar", "field 'text' is not an array",
                () -> JsonValues.getArray(object, "text"));

        check("JsonValues.getObject missing key returns an empty object", JsonValues.getObject(object, "missing").isEmpty());
        check("JsonValues.getObject JSON null returns an empty object", JsonValues.getObject(object, "nil").isEmpty());
        same("JsonValues.getObject returns the object", "v", JsonValues.getObject(object, "nested").getString("k"));
        expectIllegalArgumentException("JsonValues.getObject rejects an array", "field 'array' is not an object",
                () -> JsonValues.getObject(object, "array"));
    }

    private static void testFileIoService(Path root) throws Exception {
        expectIllegalArgumentException("FileIoService rejects a null metrics dir", "metricsDir must not be blank",
                () -> new FileIoService(null));
        expectIllegalArgumentException("FileIoService rejects a blank metrics dir", "metricsDir must not be blank",
                () -> new FileIoService("   "));

        Path d = dir(root, "fileio");
        expectIllegalArgumentException("writeCsvFile rejects a null filename", "filename must not be blank",
                () -> fio(d).writeCsvFile(null, new ArrayList<String[]>()));
        expectIllegalArgumentException("writeCsvFile rejects a blank filename", "filename must not be blank",
                () -> fio(d).writeCsvFile("  ", new ArrayList<String[]>()));
        expectIllegalArgumentException("writeCsvFile rejects null data", "data must not be null",
                () -> fio(d).writeCsvFile("custom.csv", null));

        fio(d).writeCsvFile("custom.csv", Arrays.asList(new String[]{"a", "b"}, new String[]{"c"}));
        same("writeCsvFile writes unknown filenames without schema checks", "a,b\nc\n", content(d, "custom.csv"));

        Path empty = dir(root, "fileio-empty");
        fio(empty).writeCsvFile("custom.csv", new ArrayList<String[]>());
        check("writeCsvFile with empty data creates an empty file",
                Files.exists(empty.resolve("custom.csv")) && Files.size(empty.resolve("custom.csv")) == 0);

        List<String[]> wrongHeader = new ArrayList<>();
        wrongHeader.add(new String[]{"policyName"});
        expectIllegalArgumentException("writeCsvFile rejects a wrong schema header",
                "first row must match the header of policy_violations.csv",
                () -> fio(d).writeCsvFile(FilenameInfo.policyViolationsCsvFile, wrongHeader));

        List<String[]> badRow = new ArrayList<>();
        badRow.add(FilenameInfo.policyViolationsFileHeader.clone());
        badRow.add(new String[7]);
        badRow.add(new String[2]);
        expectIllegalArgumentException("writeCsvFile rejects a short row",
                "row 3 has 2 fields, expected 7",
                () -> fio(d).writeCsvFile(FilenameInfo.policyViolationsCsvFile, badRow));

        List<String[]> nullRow = new ArrayList<>();
        nullRow.add(FilenameInfo.policyViolationsFileHeader.clone());
        nullRow.add(null);
        expectIllegalArgumentException("writeCsvFile rejects a null row", "row 2 must not be null",
                () -> fio(d).writeCsvFile(FilenameInfo.policyViolationsCsvFile, nullRow));

        Path encoded = dir(root, "fileio-encoding");
        List<String[]> encodedData = new ArrayList<>();
        encodedData.add(new String[]{"h"});
        encodedData.add(new String[]{"café, \"x\"\nline"});
        fio(encoded).writeCsvFile("custom.csv", encodedData);
        byte[] bytes = Files.readAllBytes(encoded.resolve("custom.csv"));
        String text = new String(bytes, StandardCharsets.UTF_8);
        same("writeCsvFile escapes fields, keeps UTF-8 and uses LF", "h\n\"café, \"\"x\"\"\nline\"\n", text);
        check("writeCsvFile output contains no CR", text.indexOf('\r') < 0);

        List<String[]> second = new ArrayList<>();
        second.add(new String[]{"only"});
        fio(encoded).writeCsvFile("custom.csv", second);
        same("writeCsvFile overwrites the previous file", "only\n", content(encoded, "custom.csv"));

        Path nested = dir(root, "fileio-nested").resolve("a").resolve("b");
        List<String[]> nestedData = new ArrayList<>();
        nestedData.add(new String[]{"x"});
        fio(nested).writeCsvFile("custom.csv", nestedData);
        check("writeCsvFile creates missing directories", Files.exists(nested.resolve("custom.csv")));
    }

    private static void testFilenameInfo() {
        same("application_evaluations file name", "application_evaluations.csv", FilenameInfo.applicationEvaluationsCsvFile);
        same("application_evaluations header", "applicationName|evaluationDate|stage",
                join(FilenameInfo.applicationEvaluationsFileHeader));
        same("policy_violations header", "policyName|reason|applicationName|openTime|component|stage|threatLevel",
                join(FilenameInfo.policyViolationsFileHeader));
        same("waivers header", "applicationName|stage|packageUrl|policyName|threatLevel|comment|createDate|expiryTime",
                join(FilenameInfo.waiversFileHeader));
        same("quarantined_components header", "packageUrl|repository|quarantineDate",
                join(FilenameInfo.quarantinedComponentsFileHeader));
        same("quarantined_components_summary header",
                "repositoryCount|quarantineEnabledCount|quarantineEnabled|totalComponentCount|quarantinedComponentCount",
                join(FilenameInfo.quarantinedComponentsSummaryFileHeader));
        same("autoreleased_from_quarantine_components header", "displayName|repository|quarantineDate|dateCleared",
                join(FilenameInfo.autoReleasedFromQuarantineComponentsFileHeader));
        same("autoreleased_from_quarantine_components_summary header", "MTD|YTD",
                join(FilenameInfo.autoReleasedFromQuarantineSummaryFileHeader));
        same("autoreleased_from_quarantine_config header", "id|name|autoReleaseQuarantineEnabled",
                join(FilenameInfo.autoReleasedFromQuarantineConfigFileHeader));
        same("successmetrics file name is unchanged", "successmetrics.csv", FilenameInfo.successMetricsCsvFile);
    }

    private static void testUtilService() {
        same("UtilService.removeLastChar(empty) returns empty", "", UtilService.removeLastChar(""));
        check("UtilService.removeLastChar(null) returns null", UtilService.removeLastChar(null) == null);
        same("UtilService.removeLastChar removes exactly one character", "ab", UtilService.removeLastChar("abc"));
    }

    private static void testPolicyIdsService() {
        JsonObject policies = parseObject("{\"policies\":["
                + "{\"id\":\"1\",\"name\":\"Security-High\"},"
                + "{\"id\":\"2\",\"name\":\"Not-Recognised\"},"
                + "{\"id\":\"3\",\"name\":\"License-Banned\"},"
                + "{\"id\":\"4\",\"name\":\"Integrity-Rating\"}]}");
        same("PolicyIdsService keeps the policies array order",
                "/policyViolations?p=1&p=3&p=4",
                new PolicyIdsService().buildPolicyViolationsEndpoint(policies));
        same("PolicyIdsService without matching policies returns an empty query",
                "/policyViolations?",
                new PolicyIdsService().buildPolicyViolationsEndpoint(parseObject("{\"policies\":[]}")));
        same("PolicyIdsService without a policies key returns an empty query",
                "/policyViolations?",
                new PolicyIdsService().buildPolicyViolationsEndpoint(parseObject("{\"other\":true}")));
        same("PolicyIdsService with a null object returns an empty query",
                "/policyViolations?",
                new PolicyIdsService().buildPolicyViolationsEndpoint(null));
    }

    private static void testApplicationEvaluations(Path root) throws Exception {
        Path d = dir(root, "application-evaluations");
        String json = "{\"results\":["
                + "{\"reportDataUrl\":\"a/b/c/MyApp/d\",\"evaluationDate\":\"2022-01-01\",\"stage\":\"build\"},"
                + "{\"stage\":\"release\"}]}";
        String expected = "applicationName,evaluationDate,stage\nMyApp,2022-01-01,build\n,,release\n";
        same("ApplicationEvaluations object overload", expected,
                viaObject(new ApplicationEvaluations(), d, FilenameInfo.applicationEvaluationsCsvFile, json));
        String arrayJson = "["
                + "{\"reportDataUrl\":\"a/b/c/MyApp/d\",\"evaluationDate\":\"2022-01-01\",\"stage\":\"build\"},"
                + "{\"stage\":\"release\"}]";
        same("ApplicationEvaluations reader overload", expected,
                viaReader(new ApplicationEvaluations(), d, FilenameInfo.applicationEvaluationsCsvFile, arrayJson));
        same("ApplicationEvaluations short reportDataUrl produces an empty name",
                "applicationName,evaluationDate,stage\n,,\n",
                viaObject(new ApplicationEvaluations(), d, FilenameInfo.applicationEvaluationsCsvFile,
                        "{\"results\":[{\"reportDataUrl\":\"abc\"}]}"));
    }

    private static void testWaivers(Path root) throws Exception {
        Path d = dir(root, "waivers");
        String json = "{\"applicationWaivers\":[{\"application\":{\"publicId\":\"App1\"},\"stages\":["
                + "{\"stageId\":\"build\",\"componentPolicyViolations\":[{\"component\":{\"packageUrl\":\"pkg:npm/x@1\"},"
                + "\"waivedPolicyViolations\":[{\"policyName\":\"Security-High\",\"threatLevel\":7,"
                + "\"policyWaiver\":{\"comment\":null,\"createTime\":\"2022-01-01T00:00:00.000Z\",\"expiryTime\":\"2023-01-01T00:00:00.000Z\"}}]}]}]}],"
                + "\"repositoryWaivers\":[{\"repository\":{\"publicId\":\"Repo1\"},\"stages\":["
                + "{\"stageId\":\"release\",\"componentPolicyViolations\":[{\"component\":{\"packageUrl\":\"pkg:maven/y@2\"},"
                + "\"waivedPolicyViolations\":[{\"policyName\":\"License-Banned\"}]}]}]}]}";
        String expected = "applicationName,stage,packageUrl,policyName,threatLevel,comment,createDate,expiryTime\n"
                + "App1,build,pkg:npm/x@1,Security-High,7,,2022-01-01T00:00:00.000Z,2023-01-01T00:00:00.000Z\n"
                + "Repo1,release,pkg:maven/y@2,License-Banned,0,,,\n";
        same("Waivers application rows come first and missing values are empty", expected,
                viaObject(new Waivers(), d, FilenameInfo.waiversCsvFile, json));
        same("Waivers reader overload matches the object overload", expected,
                viaReader(new Waivers(), d, FilenameInfo.waiversCsvFile, json));
        same("Waivers missing nested arrays are skipped",
                "applicationName,stage,packageUrl,policyName,threatLevel,comment,createDate,expiryTime\n",
                viaObject(new Waivers(), d, FilenameInfo.waiversCsvFile,
                        "{\"applicationWaivers\":[{\"application\":{\"publicId\":\"App2\"}}]}"));
        same("Waivers missing application object yields an empty name",
                "applicationName,stage,packageUrl,policyName,threatLevel,comment,createDate,expiryTime\n"
                        + ",s,,,0,,,\n",
                viaObject(new Waivers(), d, FilenameInfo.waiversCsvFile,
                        "{\"applicationWaivers\":[{\"stages\":[{\"stageId\":\"s\",\"componentPolicyViolations\":["
                                + "{\"waivedPolicyViolations\":[{}]}]}]}]}"));
    }

    private static void testPolicyViolations(Path root) throws Exception {
        Path d = dir(root, "policy-violations");
        String json = "{\"applicationViolations\":[{"
                + "\"application\":{\"publicId\":\"App1\"},"
                + "\"policyViolations\":["
                + "{\"policyName\":\"Security-High\",\"stageId\":\"build\",\"openTime\":\"2022-01-01\",\"threatLevel\":9,"
                + "\"component\":{\"packageUrl\":\"pkg:npm/x@1\"},"
                + "\"constraintViolations\":[{\"reasons\":[{\"reference\":{\"value\":\"CVE-2\"}},"
                + "{\"reference\":{\"value\":\"CVE-1\"}},{\"reference\":{\"value\":\"CVE-2\"}}]}]},"
                + "{\"policyName\":\"integrity-rating\",\"stageId\":\"release\",\"openTime\":\"2022-02-02\",\"threatLevel\":5,"
                + "\"component\":{\"packageUrl\":\"pkg:maven/y@2\"},\"constraintViolations\":[{}]},"
                + "{\"policyName\":\"License-Banned\",\"stageId\":\"build\",\"openTime\":\"2022-03-03\",\"threatLevel\":3,"
                + "\"component\":{\"packageUrl\":\"pkg:npm/z@3\"},"
                + "\"constraintViolations\":[{\"reasons\":[{\"reason\":\"Found license ('GPL-3.0')\"},"
                + "{\"reason\":\"(MIT)\"},{\"reason\":\"no parens here\"},{\"reason\":\"Duplicate ('GPL-3.0')\"}]}]},"
                + "{\"policyName\":\"Something-Else\",\"constraintViolations\":[{\"reasons\":[{\"reference\":{\"value\":\"CVE-9\"}}]}]}"
                + "]}]}";
        String expected = "policyName,reason,applicationName,openTime,component,stage,threatLevel\n"
                + "Security-High,CVE-2:CVE-1,App1,2022-01-01,pkg:npm/x@1,build,9\n"
                + "integrity-rating,Integrity-Rating,App1,2022-02-02,pkg:maven/y@2,release,5\n"
                + "License-Banned,GPL-3.0:MIT,App1,2022-03-03,pkg:npm/z@3,build,3\n"
                + "Something-Else,,App1,,,,0\n";
        same("PolicyViolations deduplicates reasons in first-occurrence order", expected,
                viaObject(new PolicyViolations(), d, FilenameInfo.policyViolationsCsvFile, json));
        same("PolicyViolations reader overload matches the object overload", expected,
                viaReader(new PolicyViolations(), d, FilenameInfo.policyViolationsCsvFile, json));
        same("PolicyViolations without applicationViolations writes only the header",
                "policyName,reason,applicationName,openTime,component,stage,threatLevel\n",
                viaObject(new PolicyViolations(), d, FilenameInfo.policyViolationsCsvFile, "{}"));
        same("PolicyViolations missing constraintViolations writes no rows",
                "policyName,reason,applicationName,openTime,component,stage,threatLevel\n",
                viaObject(new PolicyViolations(), d, FilenameInfo.policyViolationsCsvFile,
                        "{\"applicationViolations\":[{\"application\":{\"publicId\":\"A\"},"
                                + "\"policyViolations\":[{\"policyName\":\"Security-High\"}]}]}"));
    }

    private static void testQuarantinedComponents(Path root) throws Exception {
        Path d = dir(root, "quarantined-components");
        String json = "{\"results\":["
                + "{\"displayName\":\"A,B\",\"repository\":\"repo1\",\"quarantineDate\":\"2022-01-01\"},"
                + "{\"displayName\":\"C\"}]}";
        String expected = "packageUrl,repository,quarantineDate\n\"A,B\",repo1,2022-01-01\nC,,\n";
        same("QuarantinedComponents escapes values and keeps input order", expected,
                viaObject(new QuarantinedComponents(), d, FilenameInfo.quarantinedComponentsCsvFile, json));
        same("QuarantinedComponents reader overload matches the object overload", expected,
                viaReader(new QuarantinedComponents(), d, FilenameInfo.quarantinedComponentsCsvFile, json));
    }

    private static void testAutoReleasedFromQuarantineComponents(Path root) throws Exception {
        Path d = dir(root, "auto-released-components");
        String json = "{\"results\":["
                + "{\"displayName\":\"A\",\"repository\":\"r\",\"quarantineDate\":\"d\",\"dateCleared\":\"c\"},{}]}";
        String expected = "displayName,repository,quarantineDate,dateCleared\nA,r,d,c\n,,,\n";
        same("AutoReleasedFromQuarantineComponents writes four fields", expected,
                viaObject(new AutoReleasedFromQuarantineComponents(), d,
                        FilenameInfo.autoReleasedFromQuarantineComponentsCsvFile, json));
        same("AutoReleasedFromQuarantineComponents reader overload matches the object overload", expected,
                viaReader(new AutoReleasedFromQuarantineComponents(), d,
                        FilenameInfo.autoReleasedFromQuarantineComponentsCsvFile, json));
    }

    private static void testQuarantinedComponentsSummary(Path root) throws Exception {
        Path d = dir(root, "quarantined-summary");
        String json = "{\"repositoryCount\":3,\"quarantineEnabledRepositoryCount\":2,\"quarantineEnabled\":true,"
                + "\"totalComponentCount\":10,\"quarantinedComponentCount\":4}";
        String expected = "repositoryCount,quarantineEnabledCount,quarantineEnabled,totalComponentCount,quarantinedComponentCount\n"
                + "3,2,true,10,4\n";
        same("QuarantinedComponentsSummary writes the corrected header and values", expected,
                viaObject(new QuarantinedComponentsSummary(), d, FilenameInfo.quarantinedComponentsSummaryCsvFile, json));
        same("QuarantinedComponentsSummary reader overload matches the object overload", expected,
                viaReader(new QuarantinedComponentsSummary(), d, FilenameInfo.quarantinedComponentsSummaryCsvFile, json));
        same("QuarantinedComponentsSummary defaults missing keys",
                "repositoryCount,quarantineEnabledCount,quarantineEnabled,totalComponentCount,quarantinedComponentCount\n"
                        + "0,0,false,0,0\n",
                viaObject(new QuarantinedComponentsSummary(), d, FilenameInfo.quarantinedComponentsSummaryCsvFile, "{}"));
    }

    private static void testAutoReleasedFromQuarantineSummary(Path root) throws Exception {
        Path d = dir(root, "auto-released-summary");
        String json = "{\"autoReleaseQuarantineCountMTD\":5,\"autoReleaseQuarantineCountYTD\":11}";
        String expected = "MTD,YTD\n5,11\n";
        same("AutoReleasedFromQuarantineSummary writes both counters", expected,
                viaObject(new AutoReleasedFromQuarantineSummary(), d, FilenameInfo.autoReleasedFromQuarantineSummaryCsvFile, json));
        same("AutoReleasedFromQuarantineSummary reader overload matches the object overload", expected,
                viaReader(new AutoReleasedFromQuarantineSummary(), d, FilenameInfo.autoReleasedFromQuarantineSummaryCsvFile, json));
        same("AutoReleasedFromQuarantineSummary defaults missing keys", "MTD,YTD\n0,0\n",
                viaObject(new AutoReleasedFromQuarantineSummary(), d, FilenameInfo.autoReleasedFromQuarantineSummaryCsvFile, "{}"));
    }

    private static void testAutoReleasedFromQuarantineConfig(Path root) throws Exception {
        Path d = dir(root, "auto-released-config");
        String arrayJson = "["
                + "{\"id\":\"1\",\"name\":\"n,am\\\"e\",\"autoReleaseQuarantineEnabled\":true},"
                + "{\"id\":\"2\",\"name\":\"plain\",\"autoReleaseQuarantineEnabled\":\"TRUE\"},"
                + "{}]";
        String expected = "id,name,autoReleaseQuarantineEnabled\n"
                + "1,\"n,am\"\"e\",true\n"
                + "2,plain,true\n"
                + ",,false\n";
        same("AutoReleasedFromQuarantineConfig reader overload escapes and coerces", expected,
                viaReader(new AutoReleasedFromQuarantineConfig(), d, FilenameInfo.autoReleasedFromQuarantineConfigCsvFile, arrayJson));
        String objectJson = "{\"results\":" + arrayJson + "}";
        same("AutoReleasedFromQuarantineConfig object overload matches the reader overload", expected,
                viaObject(new AutoReleasedFromQuarantineConfig(), d, FilenameInfo.autoReleasedFromQuarantineConfigCsvFile, objectJson));
    }

    private static List<String[]> single(String[] record) {
        List<String[]> records = new ArrayList<>();
        records.add(record);
        return records;
    }

    private static void testCsvParsing() {
        sameRows("CsvUtil.parseRecord splits plain fields", rows(new String[]{"a", "b"}), single(CsvUtil.parseRecord("a,b")));
        sameRows("CsvUtil.parseRecord keeps empty fields", rows(new String[]{"a", "", "b"}), single(CsvUtil.parseRecord("a,,b")));
        sameRows("CsvUtil.parseRecord handles quoted commas", rows(new String[]{"a,b", "c"}), single(CsvUtil.parseRecord("\"a,b\",c")));
        sameRows("CsvUtil.parseRecord undoubles quotes", rows(new String[]{"a\"b"}), single(CsvUtil.parseRecord("\"a\"\"b\"")));
        sameRows("CsvUtil.parseRecord parses an empty quoted field", rows(new String[]{""}), single(CsvUtil.parseRecord("\"\"")));
        sameRows("CsvUtil.parseRecord keeps a trailing empty field", rows(new String[]{"a", ""}), single(CsvUtil.parseRecord("a,")));
        sameRows("CsvUtil.parseRecord treats a quote inside an unquoted field literally", rows(new String[]{"ab\"cd"}), single(CsvUtil.parseRecord("ab\"cd")));
        expectIllegalArgumentException("CsvUtil.parseRecord rejects an unterminated quote", "unterminated quoted field",
                () -> CsvUtil.parseRecord("\"abc"));

        sameRows("CsvUtil.parse of empty input returns no records", new ArrayList<String[]>(), CsvUtil.parse(""));
        sameRows("CsvUtil.parse reads LF records and drops the trailing terminator",
                rows(new String[]{"a", "b"}, new String[]{"c", "d"}), CsvUtil.parse("a,b\nc,d\n"));
        sameRows("CsvUtil.parse accepts CRLF terminators",
                rows(new String[]{"a", "b"}, new String[]{"c", "d"}), CsvUtil.parse("a,b\r\nc,d"));
        sameRows("CsvUtil.parse keeps newlines inside quoted fields",
                rows(new String[]{"line1\nline2", "x"}), CsvUtil.parse("\"line1\nline2\",x\n"));
        sameRows("CsvUtil.parse(one newline) is one empty record", rows(new String[]{""}), CsvUtil.parse("\n"));
    }

    private static void testReadCsvFile(Path root) throws Exception {
        Path d = dir(root, "read-csv");
        sameRows("readCsvFile of a missing file returns no records", new ArrayList<String[]>(), fio(d).readCsvFile("nope.csv"));
        expectIllegalArgumentException("readCsvFile rejects a blank filename", "filename must not be blank",
                () -> fio(d).readCsvFile("   "));

        List<String[]> original = new ArrayList<>();
        original.add(new String[]{"a,b", "x\"y", " lead"});
        original.add(new String[]{"m\nn", "", null});
        original.add(new String[]{"caf\u00e9 \u2603"});
        fio(d).writeCsvFile("custom.csv", original);

        List<String[]> expected = new ArrayList<>();
        expected.add(new String[]{"a,b", "x\"y", " lead"});
        expected.add(new String[]{"m\nn", "", ""});
        expected.add(new String[]{"caf\u00e9 \u2603"});
        sameRows("writeCsvFile/readCsvFile round-trip every field", expected, fio(d).readCsvFile("custom.csv"));

        fio(d).writeCsvFile("empty.csv", new ArrayList<String[]>());
        sameRows("readCsvFile of an empty file returns no records", new ArrayList<String[]>(), fio(d).readCsvFile("empty.csv"));
    }

    private static void testReasonFormatter() {
        JsonObject cveViolation = parseObject("{\"reasons\":[{\"reference\":{\"value\":\"CVE-2\"}},"
                + "{\"reference\":{\"value\":\"CVE-1\"}},{\"reference\":{\"value\":\"CVE-2\"}}]}");
        same("ReasonFormatter security reasons are deduplicated in order", "CVE-2:CVE-1",
                ReasonFormatter.reasonFor("Security-High", cveViolation));
        JsonObject licenseViolation = parseObject("{\"reasons\":[{\"reason\":\"Found license ('GPL-3.0')\"},"
                + "{\"reason\":\"(MIT)\"},{\"reason\":\"no parens\"},{\"reason\":\"Duplicate ('GPL-3.0')\"}]}");
        same("ReasonFormatter license reasons are parsed and deduplicated", "GPL-3.0:MIT",
                ReasonFormatter.reasonFor("License-Banned", licenseViolation));
        same("ReasonFormatter integrity rating is case-insensitive", "Integrity-Rating",
                ReasonFormatter.reasonFor("integrity-rating", parseObject("{}")));
        same("ReasonFormatter unknown policy returns empty", "", ReasonFormatter.reasonFor("Not-A-Policy", cveViolation));
        same("ReasonFormatter security reasons with no reasons returns empty", "",
                ReasonFormatter.reasonFor("Security-High", parseObject("{}")));
        same("ReasonFormatter license reasons skip entries without parentheses", "",
                ReasonFormatter.reasonFor("License-Banned",
                        parseObject("{\"reasons\":[{\"reason\":\"(unterminated\"},{\"reason\":\"no parens\"}]}")));
    }

    private static void testGetRows(Path root) throws Exception {
        sameRows("ApplicationEvaluations.getRows returns header plus rows", CsvUtil.parse(
                        "applicationName,evaluationDate,stage\nApp,,\n"),
                new ApplicationEvaluations().getRows(parseObject(
                        "{\"results\":[{\"reportDataUrl\":\"a/b/c/App/d\"}]}")));
        sameRows("Waivers.getRows returns header plus rows", CsvUtil.parse(
                        "applicationName,stage,packageUrl,policyName,threatLevel,comment,createDate,expiryTime\n"),
                new Waivers().getRows(parseObject("{\"applicationWaivers\":[{\"application\":{\"publicId\":\"A\"}}]}")));
        sameRows("PolicyViolations.getRows returns header plus rows", CsvUtil.parse(
                        "policyName,reason,applicationName,openTime,component,stage,threatLevel\n"),
                new PolicyViolations().getRows(parseObject("{}")));
        sameRows("QuarantinedComponents.getRows returns header plus rows", CsvUtil.parse(
                        "packageUrl,repository,quarantineDate\nn,r,d\n"),
                new QuarantinedComponents().getRows(parseObject(
                        "{\"results\":[{\"displayName\":\"n\",\"repository\":\"r\",\"quarantineDate\":\"d\"}]}")));
        sameRows("AutoReleasedFromQuarantineComponents.getRows returns header plus rows", CsvUtil.parse(
                        "displayName,repository,quarantineDate,dateCleared\n,,,\n"),
                new AutoReleasedFromQuarantineComponents().getRows(parseObject("{\"results\":[{}]}")));
        sameRows("QuarantinedComponentsSummary.getRows returns header plus rows", CsvUtil.parse(
                        "repositoryCount,quarantineEnabledCount,quarantineEnabled,totalComponentCount,quarantinedComponentCount\n0,0,false,0,0\n"),
                new QuarantinedComponentsSummary().getRows(parseObject("{}")));
        sameRows("AutoReleasedFromQuarantineSummary.getRows returns header plus rows", CsvUtil.parse(
                        "MTD,YTD\n0,0\n"),
                new AutoReleasedFromQuarantineSummary().getRows(parseObject("{}")));
        sameRows("AutoReleasedFromQuarantineConfig.getRows returns header plus rows", CsvUtil.parse(
                        "id,name,autoReleaseQuarantineEnabled\n,,false\n"),
                new AutoReleasedFromQuarantineConfig().getRows(parseObject("{\"results\":[{}]}")));
    }
}
