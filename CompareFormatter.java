import com.kylin.plsql.core.format.FormatOptions;
import com.kylin.plsql.core.format.SqlFormatter;
import com.kylin.plsql.core.format.plsql.PlSqlFormatter;
import com.kylin.plsql.core.format.plsql.model.FormatResult;

import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.*;

public class CompareFormatter {

    static class TestCase {
        final String name;
        final String sql;
        boolean isSimple;

        TestCase(String name, String sql) {
            this.name = name;
            this.sql = sql;
        }
    }

    static class Result {
        final String name;
        final String sql;
        final String oldResult;
        final String newResult;
        final boolean oldIsSimple;
        final boolean identical;
        final int newScore;
        final boolean newFallback;

        Result(String name, String sql, String oldResult, String newResult,
               boolean oldIsSimple, int newScore, boolean newFallback) {
            this.name = name;
            this.sql = sql;
            this.oldResult = oldResult;
            this.newResult = newResult;
            this.oldIsSimple = oldIsSimple;
            this.identical = oldResult != null && oldResult.equals(newResult);
            this.newScore = newScore;
            this.newFallback = newFallback;
        }
    }

    public static void main(String[] args) throws Exception {
        FormatOptions opts = new FormatOptions();

        // 1. Build test cases - comprehensive coverage
        List<TestCase> cases = buildTestCases();

        // 2. Run comparison
        List<Result> results = new ArrayList<>();
        System.err.println("Running comparison test (" + cases.size() + " cases)...\n");

        for (TestCase tc : cases) {
            String oldResult = runOldFormatter(tc.sql, opts);
            FormatResult newFr = PlSqlFormatter.format(tc.sql, opts);
            String newResult = newFr.getFormattedText();
            int score = newFr.getQualityScore();
            boolean fallback = newFr.isFallback();

            results.add(new Result(tc.name, tc.sql, oldResult, newResult,
                tc.isSimple, score, fallback));
        }

        // 3. Print report and write to file
        String report = buildReport(results);
        String filePath = "compare_result.txt";
        Files.writeString(Paths.get(filePath), report, java.nio.charset.StandardCharsets.UTF_8);
        System.out.println("Report written to: " + new java.io.File(filePath).getAbsolutePath());
        System.out.println("--- Console summary follows ---\n");
        System.out.println(report);
    }

    static String buildReport(List<Result> results) {
        var sb = new StringBuilder();

        int identicalCount = 0, diffCount = 0, failOldCount = 0, failNewCount = 0;

        sb.append("==============================================================================\n");
        sb.append("Test item              | Route    | NewEng | Identical? | Score  | Fallback\n");
        sb.append("==============================================================================\n");
        for (Result r : results) {
            if (r.oldResult == null) failOldCount++;
            if (r.newResult == null) failNewCount++;
            if (r.identical) identicalCount++; else diffCount++;

            String oldTag = r.oldIsSimple ? "SIMPLE" : "COMPLEX";
            String status = r.identical ? "YES" : "NO";
            String newQuality = r.newFallback ? "FALLBACK" : String.valueOf(r.newScore);
            String newState = r.newFallback ? "FAIL" : "OK";
            sb.append(String.format("%-22s| %-7s| %-6s| %-10s| %-6s|%s\n",
                truncate(r.name, 20), oldTag, newState, status, newQuality, ""));
        }
        sb.append("==============================================================================\n");

        sb.append("\n=== SUMMARY ===\n");
        sb.append("Total: ").append(results.size()).append("\n");
        sb.append("Identical output: ").append(identicalCount)
            .append(" (").append(identicalCount * 100 / results.size()).append("%)\n");
        sb.append("Different output: ").append(diffCount)
            .append(" (").append(diffCount * 100 / results.size()).append("%)\n");
        sb.append("Old engine failure: ").append(failOldCount).append("\n");
        sb.append("New engine failure: ").append(failNewCount).append("\n");

        sb.append("\n\n=== DETAILED DIFFERENCES ===\n");
        for (Result r : results) {
            if (!r.identical) {
                sb.append("\n>>>>>>>>>> [").append(r.name).append("] <<<<<<<<<<\n");
                sb.append("--- SQL ---\n");
                sb.append(r.sql.trim()).append("\n");
                sb.append("--- OLD(").append(r.oldIsSimple ? "formatSimple" : "->PlSqlFormatter=same as new").append(") ---\n");
                sb.append(r.oldResult != null ? r.oldResult : "[EXCEPTION]").append("\n");
                sb.append("--- NEW(PlSqlFormatter) ---\n");
                sb.append(r.newResult != null ? r.newResult : "[EXCEPTION]").append("\n");
                sb.append("--- Score=").append(r.newScore).append(" fallback=").append(r.newFallback).append(" ---\n");
            }
        }

        return sb.toString();
    }

    static String runOldFormatter(String sql, FormatOptions opts) {
        try {
            // Try SqlFormatter.format() first - for simple SQL it dispatches to formatSimple,
            // for complex it delegates to PlSqlFormatter
            String result = SqlFormatter.format(sql, opts);
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "~";
    }

    static List<TestCase> buildTestCases() {
        List<TestCase> cases = new ArrayList<>();

        // ======== 1. Simple DQL ========
        cases.add(new TestCase("SELECT_1", "SELECT employee_id, first_name, last_name, salary FROM employees WHERE department_id = 10 ORDER BY last_name"));
        cases.add(new TestCase("SELECT_2", "SELECT e.first_name, e.salary, d.department_name FROM employees e JOIN departments d ON e.department_id = d.department_id WHERE e.salary > 5000"));
        cases.add(new TestCase("SELECT_3", "SELECT * FROM (SELECT employee_id, salary FROM employees WHERE department_id = 20) WHERE rownum <= 5"));

        // ======== 2. Simple DML ========
        cases.add(new TestCase("INSERT_VALUES", "INSERT INTO employees (employee_id, first_name, last_name, hire_date) VALUES (100, 'John', 'Doe', SYSDATE)"));
        cases.add(new TestCase("INSERT_SELECT", "INSERT INTO high_salary_emps SELECT * FROM employees WHERE salary > 10000"));
        cases.add(new TestCase("UPDATE", "UPDATE employees SET salary = salary * 1.1, last_updated = SYSDATE WHERE department_id = 50"));
        cases.add(new TestCase("DELETE", "DELETE FROM employees WHERE employee_id = 200"));
        cases.add(new TestCase("MERGE_BASIC", "MERGE INTO employees t USING (SELECT 200 AS emp_id, 'Jane' AS name FROM dual) s ON (t.employee_id = s.emp_id) WHEN MATCHED THEN UPDATE SET t.first_name = s.name WHEN NOT MATCHED THEN INSERT (employee_id, first_name) VALUES (s.emp_id, s.name)"));

        // ======== 3. DDL ========
        cases.add(new TestCase("CREATE_TABLE", "CREATE TABLE employees (employee_id NUMBER(6) NOT NULL, first_name VARCHAR2(20), last_name VARCHAR2(25) NOT NULL, hire_date DATE DEFAULT SYSDATE) TABLESPACE users"));
        cases.add(new TestCase("CREATE_TABLE_CONST", "CREATE TABLE project_assignments (assignment_id NUMBER(10) NOT NULL, project_id NUMBER(10) NOT NULL, employee_id NUMBER(10) NOT NULL, CONSTRAINT pk_assignments PRIMARY KEY (assignment_id), CONSTRAINT fk_assign_proj FOREIGN KEY (project_id) REFERENCES projects(project_id))"));
        cases.add(new TestCase("CREATE_TABLE_STORAGE", "CREATE TABLE audit_log (log_id NUMBER(12) NOT NULL, table_name VARCHAR2(30) NOT NULL, action VARCHAR2(10) NOT NULL, CONSTRAINT pk_audit_log PRIMARY KEY (log_id)) TABLESPACE users STORAGE (INITIAL 64K NEXT 32K MINEXTENTS 1 MAXEXTENTS 121 PCTINCREASE 0) PCTFREE 10 PCTUSED 40"));
        cases.add(new TestCase("CREATE_INDEX", "CREATE INDEX idx_emp_dept ON employees (department_id, hire_date) TABLESPACE users PCTFREE 10"));
        cases.add(new TestCase("CREATE_VIEW", "CREATE OR REPLACE VIEW high_salary_v AS SELECT employee_id, first_name, last_name, salary FROM employees WHERE salary > 10000 WITH READ ONLY"));
        cases.add(new TestCase("ALTER_TABLE", "ALTER TABLE employees ADD (middle_name VARCHAR2(20), nickname VARCHAR2(30))"));
        cases.add(new TestCase("DROP_TABLE", "DROP TABLE employees PURGE"));
        cases.add(new TestCase("TRUNCATE", "TRUNCATE TABLE audit_log DROP STORAGE"));

        // ======== 4. Simple PL/SQL (IF/LOOP/CASE) ========
        cases.add(new TestCase("SIMPLE_BLOCK", "DECLARE v_name VARCHAR2(100); BEGIN SELECT first_name INTO v_name FROM employees WHERE employee_id = 100; DBMS_OUTPUT.put_line(v_name); END;"));
        cases.add(new TestCase("IF_THEN_ELSE", "DECLARE v_sal NUMBER := 5000; BEGIN IF v_sal > 10000 THEN DBMS_OUTPUT.put_line('High'); ELSIF v_sal > 5000 THEN DBMS_OUTPUT.put_line('Medium'); ELSE DBMS_OUTPUT.put_line('Low'); END IF; END;"));
        cases.add(new TestCase("SIMPLE_LOOP", "DECLARE v_i NUMBER := 1; BEGIN LOOP DBMS_OUTPUT.put_line(v_i); v_i := v_i + 1; EXIT WHEN v_i > 5; END LOOP; END;"));
        cases.add(new TestCase("FOR_LOOP", "BEGIN FOR i IN 1..10 LOOP DBMS_OUTPUT.put_line('Count: ' || i); END LOOP; END;"));
        cases.add(new TestCase("WHILE_LOOP", "DECLARE v_i NUMBER := 1; BEGIN WHILE v_i <= 5 LOOP DBMS_OUTPUT.put_line(v_i); v_i := v_i + 1; END LOOP; END;"));
        cases.add(new TestCase("CASE_EXPR", "SELECT employee_id, CASE department_id WHEN 10 THEN 'Admin' WHEN 20 THEN 'Marketing' ELSE 'Other' END AS dept_name FROM employees"));
        cases.add(new TestCase("CASE_SEARCHED", "SELECT employee_id, CASE WHEN salary > 10000 THEN 'High' WHEN salary > 5000 THEN 'Medium' ELSE 'Low' END AS sal_grade FROM employees"));

        // ======== 5. Complex PL/SQL (Function/Procedure) ========
        cases.add(new TestCase("FUNCTION", "CREATE OR REPLACE FUNCTION get_salary(p_emp_id IN NUMBER) RETURN NUMBER IS v_salary employees.salary%TYPE; BEGIN SELECT salary INTO v_salary FROM employees WHERE employee_id = p_emp_id; RETURN v_salary; EXCEPTION WHEN NO_DATA_FOUND THEN RETURN NULL; END get_salary;"));
        cases.add(new TestCase("PROCEDURE", "CREATE OR REPLACE PROCEDURE update_salary(p_emp_id IN NUMBER, p_increase IN NUMBER) IS BEGIN UPDATE employees SET salary = salary + p_increase WHERE employee_id = p_emp_id; COMMIT; EXCEPTION WHEN OTHERS THEN ROLLBACK; RAISE; END update_salary;"));
        cases.add(new TestCase("CURSOR_LOOP", "DECLARE CURSOR emp_cur IS SELECT employee_id, first_name FROM employees WHERE department_id = 10; v_emp_rec emp_cur%ROWTYPE; BEGIN OPEN emp_cur; LOOP FETCH emp_cur INTO v_emp_rec; EXIT WHEN emp_cur%NOTFOUND; DBMS_OUTPUT.put_line(v_emp_rec.first_name); END LOOP; CLOSE emp_cur; END;"));

        // ======== 6. Comments ========
        cases.add(new TestCase("COMMENT_LINE", "SELECT employee_id, -- employee identifier\nfirst_name, last_name FROM employees WHERE department_id = 10"));
        cases.add(new TestCase("COMMENT_BLOCK", "SELECT employee_id, /* This is the name */ first_name, last_name FROM employees WHERE department_id = 10"));

        // ======== 7. Set operators ========
        cases.add(new TestCase("UNION", "SELECT employee_id FROM employees UNION SELECT employee_id FROM former_employees ORDER BY employee_id"));
        cases.add(new TestCase("UNION_ALL", "SELECT * FROM sales_2023 UNION ALL SELECT * FROM sales_2024"));

        // ======== 8. Special constructs ========
        cases.add(new TestCase("BULK_COLLECT", "DECLARE TYPE emp_tab IS TABLE OF employees%ROWTYPE; v_emps emp_tab; BEGIN SELECT * BULK COLLECT INTO v_emps FROM employees WHERE department_id = 10; END;"));
        cases.add(new TestCase("WITH_CTE", "WITH dept_stats AS (SELECT department_id, COUNT(*) AS emp_count, AVG(salary) AS avg_sal FROM employees GROUP BY department_id) SELECT department_id, emp_count, avg_sal FROM dept_stats WHERE emp_count > 5"));
        cases.add(new TestCase("FORALL", "DECLARE TYPE id_list IS TABLE OF NUMBER; v_ids id_list := id_list(100, 101, 102); BEGIN FORALL i IN 1..v_ids.COUNT UPDATE employees SET salary = salary * 1.1 WHERE employee_id = v_ids(i); COMMIT; END;"));
        cases.add(new TestCase("EXECUTE_IMMEDIATE", "DECLARE v_sql VARCHAR2(200); BEGIN v_sql := 'DELETE FROM employees WHERE employee_id = :1'; EXECUTE IMMEDIATE v_sql USING 100; COMMIT; END;"));

        // ======== 9. Empty/edge ========
        cases.add(new TestCase("EMPTY", ""));
        cases.add(new TestCase("BLANK", "  \n  "));
        cases.add(new TestCase("COMMENT_ONLY", "-- just a comment"));
        cases.add(new TestCase("SEMICOLON", "SELECT 1 FROM DUAL;"));

        // Mark simple vs complex using isSimpleSql
        var m = Arrays.stream(SqlFormatter.class.getDeclaredMethods())
            .filter(mt -> mt.getName().equals("isSimpleSql"))
            .findFirst().orElse(null);
        if (m != null) {
            m.setAccessible(true);
            for (TestCase tc : cases) {
                try {
                    tc.isSimple = (boolean) m.invoke(null, tc.sql);
                } catch (Exception e) {
                    tc.isSimple = false;
                }
            }
        }

        return cases;
    }
}
