import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FlybookDBGenerator {

    private Connection connection = null;

    private final String tblPrefix = "";
    private final String colPrefix = "c_";
    private final String packageName = "hlrv.flybook";
    private final String dbFileName = "flybook.db";

    // @formatter:off
    
    // List of column names/types/constraints
    
    private String[][] tableDescriptors = {
            { 
            "Users",
            "username           TEXT            PRIMARY KEY",
            "passwd             TEXT",
            "passwd_salt        TEXT",
            "firstname          TEXT", 
            "lastname           TEXT", 
            "role               TINYINT",
            "email              TEXT",
            "optlock            INTEGER         @VERSION"        
            },
            
            {
            "FlightEntries",
            "flight_id          INTEGER         PRIMARY KEY", 
            "username           TEXT            REFERENCES Users (c_username)",
            "date               DATETIME", 
            "aircraft           TEXT            REFERENCES Aircrafts (c_register)",
            "departure_time     DATETIME",
            "departure_airport  INTEGER", 
            "landing_time       DATETIME",
            "landing_airport    INTEGER", 
            "onblock_time       INTEGER", 
            "offblock_time      INTEGER",
            "flight_type        INTEGER", 
            "ifr_time           TEXT", 
            "notes              TEXT",
            "optlock            INTEGER         @VERSION"
            },
            {
            "Airports",
            "id                 INTEGER         PRIMARY KEY",
            "code               CHAR(4)", 
            "country            TEXT", 
            "city               TEXT", 
            "name               TEXT",
            "location           TEXT",
            "optlock            INTEGER         @VERSION"
            },
            {
            "Aircrafts",
            "register           TEXT            PRIMARY KEY",
            "class              INTEGER", 
            "capacity           INTEGER", 
            "weight             INTEGER",
            "optlock            INTEGER         @VERSION"
            }};
    
    // Note: VERSION is dummy constraint and won't be really used
    
 // @formatter:on

    private String[] usersTableDescriptor = tableDescriptors[0];
    private String[] flightEntriesTableDescriptor = tableDescriptors[1];
    private String[] airportsTableDescriptor = tableDescriptors[2];
    private String[] aircraftsTableDescriptor = tableDescriptors[3];

    private final TableEntry[] tableEntries = {
            new TableEntry(usersTableDescriptor),
            new TableEntry(flightEntriesTableDescriptor),
            new TableEntry(airportsTableDescriptor),
            new TableEntry(aircraftsTableDescriptor) };

    private final Random airportRandomizer = new Random(0);

    public FlybookDBGenerator() {

        initConnection();

        // for (String[] desc : tableDescriptors) {
        // createTable(desc);
        // }

        for (TableEntry e : tableEntries) {
            createTable(e);
        }

        // Order is important!
        populateUsersTable();
        populateAirportsTable();
        populateAircraftsTable();
        populateFlightEntriesTable();

        createTriggers();

        generateDBConstanstsClass();
    }

    // Opens sqlite-jdbc connection
    private void initConnection() {

        try {
            Class.forName("org.sqlite.JDBC");

            connection = DriverManager.getConnection("jdbc:sqlite:"
                    + dbFileName);

        } catch (ClassNotFoundException e) {
            System.err.println(e.toString());
            System.exit(1);
        } catch (SQLException e) {
            System.err.println(e.toString());
            System.exit(1);
        }

    }

    private void createTable(TableEntry entry) {

        String sqlDrop = entry.createSqlStatement_DropTable();

        String sqlCreate = entry.createSqlStatement_CreateTable();

        try {
            Statement statement = connection.createStatement();
            statement.setQueryTimeout(30);

            System.out.println(sqlCreate);

            statement.executeUpdate(sqlDrop);
            statement.executeUpdate(sqlCreate);
        } catch (SQLException e) {
            System.err.println(e.toString());
            System.exit(1);
        }

    }

    private void populateUsersTable() {

        final String[] firstNames = { "Andre", "Konstantin", "John", "Stephen",
                "Neil", "Michio" };
        final String[] lastNames = { "Konstantin", "Novoselov", "Venter",
                "Hawking", "Tyson", "Kaku" };

        try {
            connection.setAutoCommit(false);
            Statement statement = connection.createStatement();
            statement.setQueryTimeout(30);

            for (String fname : firstNames) {
                for (String lname : lastNames) {

                    TableEntry entry = generateUserData(fname, lname);
                    if (entry == null) {
                        System.out.println("Problematic user entry: " + fname
                                + " " + lname);
                        continue;
                    }

                    String sql = entry.createSqlStatement_Insert();

                    System.out.println(sql);

                    statement.executeUpdate(sql);
                }
            }

            connection.commit();
            connection.setAutoCommit(true);
        } catch (SQLException e) {
            System.err.println(e.toString());
            System.exit(1);
        }

    }

    private void populateAirportsTable() {

        // String airportSourceFile = "icaos.txt";
        String airportSourceFile = "airports.dat";

        try {

            BufferedReader in = new BufferedReader(new FileReader(
                    airportSourceFile));

            int lineCount = 0;
            int invalidLineCount = 0;
            HashSet<String> codeSet = new HashSet<String>();

            connection.setAutoCommit(false);
            Statement statement = connection.createStatement();
            statement.setQueryTimeout(30);

            String line;
            while ((line = in.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                lineCount++;

                // TableEntry entry = generateAirportData1(line);
                TableEntry entry = generateAirportData_OpenFlights_Org(line);
                if (entry == null) {
                    invalidLineCount++;
                    System.out.println("Problematic Airport entry: '" + line
                            + "'");
                    continue;
                }

                String code = entry.getColumnValue("code");
                if (codeSet.contains(code)) {
                    // System.out.println("Duplicate ICAOS Code: " + code);
                }
                codeSet.add(code);

                String sql = entry.createSqlStatement_Insert();

                System.out.println(sql);

                statement.executeUpdate(sql);

            }

            connection.commit();
            connection.setAutoCommit(true);

            System.out.println("Problematic Lines: " + invalidLineCount + "/"
                    + lineCount);

            System.out.println("Code Set Size: " + codeSet.size() + "/"
                    + lineCount);

            in.close();

        } catch (SQLException e) {
            System.err.println(e.toString());
            System.exit(1);
        } catch (FileNotFoundException e) {
            System.err.println(e.toString());
            System.exit(1);
        } catch (IOException e) {
            System.err.println(e.toString());
            System.exit(1);
        }

    }

    private void populateAircraftsTable() {

        try {

            connection.setAutoCommit(false);
            Statement statement = connection.createStatement();
            statement.setQueryTimeout(30);

            TableEntry entry = generateAircraftData();
            if (entry == null) {
                System.out.println("Problematic Aircraft entry: '");
                return;
            }

            String sql = entry.createSqlStatement_Insert();

            System.out.println(sql);

            statement.executeUpdate(sql);

            connection.commit();
            connection.setAutoCommit(true);

        } catch (SQLException e) {
            System.err.println(e.toString());
            System.exit(1);
        }
    }

    private void populateFlightEntriesTable() {

        try {

            connection.setAutoCommit(false);
            Statement statement = connection.createStatement();
            statement.setQueryTimeout(30);

            // Collect airport ids to randomize departure/landing port
            ArrayList<Integer> apIDs = new ArrayList<Integer>(10000);
            String col_ap_id = colPrefix + "id";
            ResultSet airportsRS = statement.executeQuery("SELECT " + col_ap_id
                    + " FROM Airports");
            while (airportsRS.next()) {
                apIDs.add(airportsRS.getInt(1));
            }

            String col_username = colPrefix + "username";

            // Collect all usernames
            ArrayList<String> usernames = new ArrayList<String>();
            ResultSet usersRS = statement.executeQuery("SELECT " + col_username
                    + " FROM Users");
            while (usersRS.next()) {
                usernames.add(usersRS.getString(1));
            }

            for (String username : usernames) {

                TableEntry entry = generateFlightData(username, apIDs);

                String sql = entry.createSqlStatement_Insert();

                System.out.println(sql);

                statement.executeUpdate(sql);
            }

            connection.commit();
            connection.setAutoCommit(true);

        } catch (SQLException e) {
            System.err.println(e.toString());
            System.exit(1);
        }

    }

    private TableEntry generateUserData(String fname, String lname) {

        String username = (fname.substring(0, 3) + lname.substring(0, 3))
                .toLowerCase();

        String passwd_plain = "abc123";
        String passwd_salt = BCrypt.gensalt();
        String passwd_hash = BCrypt.hashpw(passwd_plain, passwd_salt);
        int role = 0;
        String email = fname + "." + lname + "@mail.com";

        TableEntry entry = new TableEntry(usersTableDescriptor);

        if (!(entry.setColumnStringValue("username", username)
                && entry.setColumnStringValue("passwd", passwd_hash)
                && entry.setColumnStringValue("passwd_salt", passwd_salt)
                && entry.setColumnStringValue("firstname", fname)
                && entry.setColumnStringValue("lastname", lname)
                && entry.setColumnIntValue("role", role) && entry
                    .setColumnStringValue("email", email))) {
            System.err.println("Failed to set some value(s)");
            return null;
        }

        return entry;
    }

    private TableEntry generateAirportData_icaos_txt(String line) {
        if (!line.matches("\\s*[A-Z0-9]{4}\\s*;[^,]+(,[^,]+){2,3}")) {
            return null;
        }

        String[] splits = line.split(";|,");

        String code = splits[0].trim();
        String name = splits[1].trim();
        String city = splits[2].trim();
        String country = null;

        if (splits.length > 4) {
            String part3 = splits[3].trim();
            String part4 = splits[4].trim();
            if (part4.endsWith(" of")) {
                country = part4 + " " + part3;
            } else if (part4.equals("United States of America")) {
                if (part3.matches("[A-Z]{2}")) { // us state code
                    country = part4;
                } else {
                    city = part3;
                    country = part4;
                }
            } else {
                return null;
            }

        } else {
            country = splits[3].trim();
        }

        TableEntry entry = new TableEntry(airportsTableDescriptor);
        if (!(entry.setColumnStringValue("code", code)
                && entry.setColumnStringValue("name", name)
                && entry.setColumnStringValue("city", city)
                && entry.setColumnStringValue("country", country) && entry
                    .setColumnStringValue("location", "1.1:2.2"))) {
            System.err.println("Failed to set some value(s)");
            return null;
        }

        return entry;
    }

    private TableEntry generateAirportData_OpenFlights_Org(String line) {

        // http://openflights.org/data.html#airport

        Pattern pattern = Pattern
                .compile("^\\d+,\"([^\"]+)\",\"([^\"]+)\",\"([^\"]+)\""
                        + ",(?:\"([A-Z0-9]{3})\"|[^,]*),(?:\"([A-Z0-9]{4})\"|[^,]*)"
                        + ",(-?\\d+(?:\\.\\d+)?),(-?\\d+(?:\\.\\d+)?)"
                        + ",-?\\d+(?:\\.\\d+)?,-?\\d+(?:\\.\\d+)?,\"[EASOZNU]\"\\s*$");

        Matcher matcher = pattern.matcher(line);
        if (!matcher.matches()) {
            return null;
        }

        String name = matcher.group(1);
        String city = matcher.group(2);
        String country = matcher.group(3);

        String iataCode = matcher.group(4);
        if (iataCode == null) {
            iataCode = "";
        }

        String icaoCode = matcher.group(5);
        if (icaoCode == null) {
            icaoCode = "";
        }

        String location = matcher.group(6) + ":" + matcher.group(7);

        TableEntry entry = new TableEntry(airportsTableDescriptor);
        if (!(entry.setColumnStringValue("code", icaoCode)
                && entry.setColumnStringValue("name", name)
                && entry.setColumnStringValue("city", city)
                && entry.setColumnStringValue("country", country) && entry
                    .setColumnStringValue("location", location))) {
            System.err.println("Failed to set some value(s)");
            return null;
        }

        return entry;
    }

    private TableEntry generateAircraftData() {

        TableEntry entry = new TableEntry(aircraftsTableDescriptor);

        entry.setColumnStringValue("register", "REG123");
        entry.setColumnStringValue("class", "Light Single Engine");
        entry.setColumnIntValue("capacity", 4);
        entry.setColumnIntValue("weight", 1000);

        return entry;

    }

    private TableEntry generateFlightData(String username,
            ArrayList<Integer> apIDs) {

        TableEntry entry = new TableEntry(flightEntriesTableDescriptor);

        entry.setColumnStringValue("username", username);
        entry.setColumnValue("date", "strftime('%s','now')");
        entry.setColumnStringValue("aircraft", "REG123");

        int departureOffset = 10 + airportRandomizer.nextInt(20); // minutes
        int flyTime = 15 + airportRandomizer.nextInt(500); // minutes

        entry.setColumnValue("departure_time", "strftime('%s', 'now', '"
                + departureOffset + " minutes')");

        entry.setColumnValue("landing_time", "strftime('%s', 'now', '"
                + (departureOffset + flyTime) + " minutes')");

        int departurePort = 1;
        int landingPort = 1;

        if (apIDs.size() > 1) {
            departurePort = apIDs.get(airportRandomizer.nextInt(apIDs.size()));
            do {
                landingPort = apIDs
                        .get(airportRandomizer.nextInt(apIDs.size()));
            } while (landingPort == departurePort);
        }

        entry.setColumnIntValue("departure_airport", departurePort);
        entry.setColumnIntValue("landing_airport", landingPort);

        entry.setColumnIntValue("onblock_time", 1);
        entry.setColumnIntValue("offblock_time", 0);

        entry.setColumnIntValue("flight_type", 0);

        entry.setColumnStringValue("notes", "");

        entry.setColumnIntValue("optlock", 0);

        return entry;

    }

    private void createTriggers() {

        try {
            Statement statement = connection.createStatement();

            for (TableEntry e : tableEntries) {
                String sql = e.createSqlTrigger_incrementVersionOnRowUpdate();

                System.out.println(sql);

                statement.executeUpdate(sql);
            }

            // String tableName = tblPrefix + "FlightEntries";
            // String col_optlock = colPrefix + "optlock";
            // String col_user = colPrefix + "username";
            //
            // // Increment optlock (VERSION) column value on row update
            // StringBuilder sql = new StringBuilder();
            // sql.append("CREATE TRIGGER flightentry_optlock_updater ");
            // sql.append("AFTER UPDATE ON ").append(tableName);
            // sql.append(" FOR EACH ROW ");
            // sql.append("BEGIN ");
            // sql.append("UPDATE ").append(tableName);
            // sql.append(" SET ").append(col_optlock).append(" = ");
            // sql.append(col_optlock).append(" + 1 ");
            // sql.append("WHERE ").append(col_user).append(" = ");
            // sql.append("OLD.").append(col_user).append("; ");
            // sql.append("END");

        } catch (SQLException e) {
            System.err.println(e.toString());
        }

    }

    private void generateDBConstanstsClass() {

        final String className = "DBConstants";
        final String tab = "    ";
        final String stmnt_end = ";\n";
        final String linePrefix = "public final static String ";
        final int alignment = 40;

        StringBuilder sb = new StringBuilder();

        sb.append("package ").append(packageName).append(stmnt_end);
        sb.append("\n");
        sb.append("public final class ").append(className).append(" {\n");
        sb.append("\n");
        sb.append(tab).append("// @formatter:off\n");
        sb.append("\n");
        sb.append(tab).append(linePrefix).append("FILENAME = \"")
                .append(dbFileName).append("\"").append(stmnt_end);
        sb.append("\n");
        sb.append(tab).append(linePrefix).append("TBLPREFIX = \"")
                .append(tblPrefix).append("\"").append(stmnt_end);
        sb.append(tab).append(linePrefix).append("COLPREFIX = \"")
                .append(colPrefix).append("\"").append(stmnt_end);
        sb.append("\n");

        for (String[] desc : tableDescriptors) {

            String table = desc[0];

            String variablePrefix = "TABLE_" + table.toUpperCase();

            sb.append(tab).append(linePrefix).append(variablePrefix);
            for (int i = alignment - variablePrefix.length(); i > 0; --i) {
                sb.append(' ');
            }

            sb.append(" = TBLPREFIX + \"").append(table).append("\"")
                    .append(stmnt_end);
        }
        sb.append("\n");

        for (String[] desc : tableDescriptors) {

            String table = desc[0];

            for (int col = 1; col < desc.length; ++col) {
                String name = desc[col].substring(0, desc[col].indexOf(" "))
                        .trim();

                String variablePrefix = table.toUpperCase() + "_"
                        + name.toUpperCase();

                sb.append(tab).append(linePrefix).append(variablePrefix);

                for (int j = alignment - variablePrefix.length(); j > 0; --j) {
                    sb.append(' ');
                }

                sb.append(" = COLPREFIX + \"").append(name).append("\"")
                        .append(stmnt_end);
            }
            sb.append("\n");
        }
        sb.append(tab).append("// @formatter:on\n");
        sb.append("\n");
        sb.append("}\n");
        sb.append("\n");

        try {
            BufferedWriter out = new BufferedWriter(new FileWriter(className
                    + ".java"));
            out.write(sb.toString());
            out.close();
        } catch (IOException e) {
            System.out.println(e.toString());
            System.exit(1);
        }
    }

    /**
     * Class that expands table description string into object structure
     * representing a database table or one of it's row. Values can be set for
     * columns.
     */
    private class TableEntry {

        private String tableName;
        private ColumnData[] data;

        public TableEntry(String[] tableDescriptor) {

            tableName = tableDescriptor[0];

            data = new ColumnData[tableDescriptor.length - 1];
            for (int i = 1; i < tableDescriptor.length; ++i) {
                data[i - 1] = new ColumnData(tableDescriptor[i]);
            }
        }

        public String getTableName() {
            return tableName;
        }

        public String getKeyColumnName() {
            for (int i = 0; i < data.length; ++i) {
                if (data[i].isPrimaryKey()) {
                    return data[i].getName();
                }
            }
            return null;
        }

        public String getVersionColumnName() {
            for (int i = 0; i < data.length; ++i) {
                if (data[i].isVersion()) {
                    return data[i].getName();
                }
            }
            return null;
        }

        public String getColumnValue(String name) {
            ColumnData data = getColumnData(name);
            if (data != null) {
                return data.getValue();
            }
            return null;
        }

        public boolean setColumnIntValue(String name, int value) {
            ColumnData data = getColumnData(name);
            if (data != null) {
                data.setIntValue(value);
            }
            return data != null;
        }

        public boolean setColumnFloatValue(String name, double value) {
            ColumnData data = getColumnData(name);
            if (data != null) {
                data.setFloatValue(value);
            }
            return data != null;
        }

        public boolean setColumnValue(String name, String value) {
            ColumnData data = getColumnData(name);
            if (data != null) {
                data.setValue(value);
            }
            return data != null;
        }

        public boolean setColumnStringValue(String name, String value) {
            ColumnData data = getColumnData(name);
            if (data != null) {
                data.setStringValue(value);
            }
            return data != null;
        }

        public String createSqlStatement_DropTable() {

            StringBuilder sql = new StringBuilder();
            sql.append("DROP TABLE IF EXISTS ");
            sql.append(tblPrefix).append(tableName);
            return sql.toString();
        }

        public String createSqlStatement_CreateTable() {

            StringBuilder sql = new StringBuilder();
            sql.append("CREATE TABLE ");
            sql.append(tblPrefix).append(tableName);
            sql.append("(");
            for (int i = 0; i < data.length; ++i) {
                if (i > 0) {
                    sql.append(", ");
                }
                sql.append(data[i].toColumnDef());
            }
            sql.append(")");

            return sql.toString();
        }

        public String createSqlStatement_Insert() {

            String table = tblPrefix + tableName;

            StringBuilder sql = new StringBuilder();

            sql.append("INSERT INTO ").append(table);
            sql.append(" (");
            sql.append(commaSeparatedColumnNames(true));
            sql.append(") VALUES(");
            sql.append(commaSeparatedColumnValues(true));
            sql.append(")");

            return sql.toString();
        }

        public String createSqlTrigger_incrementVersionOnRowUpdate() {

            String table = tblPrefix + tableName;
            String version = colPrefix + getVersionColumnName();
            String key = colPrefix + getKeyColumnName();

            if (version == null || key == null) {
                return null;
            }

            StringBuilder sql = new StringBuilder();
            sql.append("CREATE TRIGGER trigger_version_").append(table);
            sql.append(" AFTER UPDATE ON ").append(table);
            sql.append(" FOR EACH ROW ");
            sql.append("BEGIN ");
            sql.append("UPDATE ").append(table);
            sql.append(" SET ").append(version).append(" = ");
            sql.append(version).append(" + 1 ");
            sql.append("WHERE ").append(key).append(" = ");
            sql.append("OLD.").append(key).append("; ");
            sql.append("END");

            return sql.toString();
        }

        private ColumnData getColumnData(String name) {
            for (ColumnData c : data) {
                if (c.getName().equals(name)) {
                    return c;
                }
            }
            return null;
        }

        private String commaSeparatedColumnNames(boolean ignoreIntegerKey) {
            StringBuilder sb = new StringBuilder();
            for (ColumnData c : data) {

                if (ignoreIntegerKey && c.isIntegerPrimaryKey()) {
                    continue;
                }

                if (c.isNullValue()) {
                    continue;
                }

                if (sb.length() > 0) {
                    sb.append(",");
                }
                sb.append(colPrefix).append(c.getName());
            }
            return sb.toString();
        }

        private String commaSeparatedColumnValues(boolean ignoreIntegerKey) {
            StringBuilder sb = new StringBuilder();
            for (ColumnData c : data) {

                if (ignoreIntegerKey && c.isIntegerPrimaryKey()) {
                    continue;
                }

                if (c.isNullValue()) {
                    continue;
                }

                if (sb.length() > 0) {
                    sb.append(",");
                }
                sb.append(c.getValue());
            }
            return sb.toString();
        }

        private class ColumnData {

            private String name;
            private String type;
            private ArrayList<String> constraints = new ArrayList<String>();
            private String value = null;
            private boolean isPrimaryKey;
            private boolean isVersion;
            private boolean hasDefaultValue;

            public ColumnData(String columnDescriptor) {
                String[] tokens = columnDescriptor.trim().split("\\s+");

                this.name = tokens[0];
                this.type = tokens[1].toUpperCase();

                if (tokens.length > 2) {

                    int primaryKeywordPos = -1;

                    for (int i = 2; i < tokens.length; ++i) {

                        String constraint = tokens[i];

                        if (constraint.equals("KEY")) {
                            if (primaryKeywordPos == i - 1) {
                                isPrimaryKey = true;
                            } else {
                                System.err.println("Singleton KEY constraint");
                            }
                        } else if (constraint.equals("@VERSION")) {
                            isVersion = true;
                            constraints.add("DEFAULT");
                            constraints.add("0");
                            constraint = ""; // VERSION is dummy constraint
                        } else if (constraint.equals("PRIMARY")) {
                            primaryKeywordPos = i;
                        } else if (constraint.equals("DEFAULT")) {
                            hasDefaultValue = true;
                        }

                        if (!constraint.isEmpty()) {
                            constraints.add(constraint);
                        }
                    }
                }
            }

            public String getName() {
                return name;
            }

            public String getValue() {
                return value;
            }

            public boolean isNullValue() {
                return value == null;
            }

            public boolean isPrimaryKey() {
                return isPrimaryKey;
            }

            public boolean isIntegerPrimaryKey() {
                return isPrimaryKey && type.equals("INTEGER");
            }

            public boolean isVersion() {
                return isVersion;
            }

            public boolean hasDefaultValue() {
                return hasDefaultValue;
            }

            public String toColumnDef() {

                StringBuilder sb = new StringBuilder();
                sb.append(colPrefix).append(name).append(" ");
                sb.append(type).append(" ");

                boolean first = true;
                for (String c : constraints) {
                    if (first) {
                        first = false;
                    } else {
                        sb.append(" ");
                    }
                    sb.append(c);
                }
                return sb.toString();
            }

            public void setIntValue(int value) {
                this.value = String.valueOf(value);
            }

            public void setFloatValue(double value) {
                this.value = String.valueOf(value);
            }

            public void setValue(String value) {
                this.value = value;
            }

            public void setStringValue(String value) {
                this.value = "'" + value.replaceAll("'", "''") + "'";
            }
        }

    }

    /**
     * @param args
     */
    public static void main(String[] args) {
        new FlybookDBGenerator();
    }
}
