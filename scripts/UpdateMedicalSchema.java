import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;

/** Restricted V002 migration; administrator credentials arrive only through stdin. */
class UpdateMedicalSchema {
    private static final String VERSION = "V002";
    private static final List<String> BASE = List.of("appointment", "department", "doctor", "patient");
    private static final List<String> NEW = List.of("doctor_schedule", "medicine", "medicine_inventory",
            "prescription", "prescription_item", "inventory_movement");
    private static final List<String> CHECKS = new ArrayList<>();
    private static String field(BufferedReader reader) throws Exception {
        String line=reader.readLine();
        if(line==null) throw new IllegalArgumentException("Missing local input");
        return new String(Base64.getDecoder().decode(line),StandardCharsets.UTF_8);
    }
    private static String json(String text) {
        return "\"" + text.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r") + "\"";
    }
    private static long scalar(Connection c,String sql,Object... values) throws SQLException {
        try(var p=c.prepareStatement(sql)){for(int i=0;i<values.length;i++)p.setObject(i+1,values[i]);
            try(var r=p.executeQuery()){if(!r.next())throw new SQLException("No scalar result");return r.getLong(1);}}
    }
    private static int change(Connection c,String sql,Object... values) throws SQLException {
        try(var p=c.prepareStatement(sql)){for(int i=0;i<values.length;i++)p.setObject(i+1,values[i]);return p.executeUpdate();}
    }
    private static long insert(Connection c,String sql,Object... values) throws SQLException {
        try(var p=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)){
            for(int i=0;i<values.length;i++)p.setObject(i+1,values[i]);p.executeUpdate();
            try(var r=p.getGeneratedKeys()){r.next();return r.getLong(1);}}
    }
    private static String table(String name) {
        if(!name.matches("[a-z_]+"))throw new IllegalArgumentException("Invalid identifier");
        return Character.toString(96)+name+Character.toString(96);
    }
    @SuppressWarnings("unchecked")
    private static Map<String,List<List<String>>> readSnapshot(Path path) throws Exception {
        try(var in=new ObjectInputStream(Files.newInputStream(path))) {
            in.setObjectInputFilter(ObjectInputFilter.Config.createFilter(
                    "maxdepth=12;maxrefs=100000;maxbytes=20000000;java.base/*;!*"));
            return (Map<String,List<List<String>>>)in.readObject();
        }
    }
    private static void assertOriginalRows(Connection c,Map<String,List<List<String>>> original) throws Exception {
        if(!original.keySet().equals(new TreeSet<>(BASE)))throw new IllegalStateException("Unexpected baseline tables");
        for(var entry:original.entrySet()){
            var columns=entry.getValue().get(0);
            var data=new ArrayList<List<String>>();
            data.add(columns);
            String select=String.join(",",columns.stream().map(UpdateMedicalSchema::table).toList());
            try(var s=c.createStatement();var rows=s.executeQuery("SELECT "+select+" FROM "+table(entry.getKey())+" ORDER BY id")){
                while(rows.next()){
                    var row=new ArrayList<String>();for(int i=1;i<=columns.size();i++)row.add(rows.getString(i));data.add(row);
                }
            }
            if(!entry.getValue().equals(data))throw new IllegalStateException("Original data changed: "+entry.getKey());
        }
    }
    private static void verifyStructure(Connection c) throws Exception {
        for(String name:BASE)if(scalar(c,"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?",name)!=1)
            throw new IllegalStateException("Missing original table");
        for(String name:NEW)if(scalar(c,"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?",name)!=1)
            throw new IllegalStateException("Missing new table");
        if(scalar(c,"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND engine='InnoDB' AND table_collation LIKE 'utf8mb4%'")!=11)
            throw new IllegalStateException("Unexpected engine, encoding or table count");
        for(String name:List.of("fk_schedule_doctor","fk_appointment_schedule_doctor","fk_inventory_medicine",
                "fk_prescription_appointment","fk_item_prescription","fk_item_medicine","fk_movement_inventory")){
            if(scalar(c,"SELECT COUNT(*) FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE() AND constraint_name=?",name)!=1)
                throw new IllegalStateException("Missing foreign key "+name);
        }
        for(String name:List.of("uk_doctor_no","uk_patient_identity_card","uk_schedule_doctor_date_slot",
                "uk_appointment_patient_active_schedule","uk_medicine_code","uk_prescription_no",
                "uk_prescription_appointment","uk_prescription_item_line","uk_inventory_movement_business")){
            if(scalar(c,"SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND constraint_type='UNIQUE' AND constraint_name=?",name)!=1)
                throw new IllegalStateException("Missing unique key "+name);
        }
        CHECKS.add("Table, engine, charset, foreign key and unique key metadata verified");
    }
    @FunctionalInterface private interface SqlAction { void run() throws Exception; }
    private static void rejected(Connection c,String label,int expected,SqlAction action) throws Exception {
        var savepoint=c.setSavepoint();
        boolean rejected=false;
        try{action.run();}catch(SQLException e){if(e.getErrorCode()!=expected)throw e;rejected=true;}
        finally{c.rollback(savepoint);c.releaseSavepoint(savepoint);}
        if(!rejected)throw new IllegalStateException("Constraint failed: "+label);
        CHECKS.add(label);
    }
    private static void exerciseConstraints(Connection c) throws Exception {
        c.setAutoCommit(false);
        try{
            String suffix=UUID.randomUUID().toString().replace("-","").substring(0,12);
            long department=insert(c,"INSERT INTO department(code,name) VALUES (?,?)","MIG_"+suffix,"迁移验证科室");
            long doctor=insert(c,"INSERT INTO doctor(department_id,name,doctor_no) VALUES (?,?,?)",department,"验证医生","DOC_"+suffix);
            long otherDoctor=insert(c,"INSERT INTO doctor(department_id,name) VALUES (?,?)",department,"其他验证医生");
            long patient=insert(c,"INSERT INTO patient(name,identity_card) VALUES (?,?)","验证患者","TEST"+suffix);
            rejected(c,"Duplicate doctor number rejected",1062,()->change(c,"INSERT INTO doctor(department_id,name,doctor_no) VALUES (?,?,?)",department,"重复工号","DOC_"+suffix));
            rejected(c,"Duplicate patient identity rejected",1062,()->change(c,"INSERT INTO patient(name,identity_card) VALUES (?,?)","重复证件","TEST"+suffix));
            long schedule=insert(c,"INSERT INTO doctor_schedule(doctor_id,work_date,time_slot,start_time,end_time,total_capacity,remaining_capacity) VALUES (?, '2099-01-01','MORNING','08:00','12:00',2,2)",doctor);
            rejected(c,"Duplicate doctor/date/slot rejected",1062,()->change(c,"INSERT INTO doctor_schedule(doctor_id,work_date,time_slot,start_time,end_time,total_capacity,remaining_capacity) VALUES (?, '2099-01-01','MORNING','08:00','12:00',2,2)",doctor));
            rejected(c,"Invalid capacity rejected",3819,()->change(c,"UPDATE doctor_schedule SET remaining_capacity=3 WHERE id=?",schedule));
            rejected(c,"Invalid time range rejected",3819,()->change(c,"UPDATE doctor_schedule SET end_time='07:00' WHERE id=?",schedule));
            long appointment=insert(c,"INSERT INTO appointment(appointment_no,patient_id,doctor_id,appointment_time,schedule_id) VALUES (?,?,?,'2099-01-01 08:30:00',?)","MIG_A_"+suffix,patient,doctor,schedule);
            rejected(c,"Duplicate active patient/schedule rejected",1062,()->change(c,"INSERT INTO appointment(appointment_no,patient_id,doctor_id,appointment_time,schedule_id) VALUES (?,?,?,'2099-01-01 08:30:00',?)","MIG_B_"+suffix,patient,doctor,schedule));
            rejected(c,"Schedule/doctor mismatch rejected",1452,()->change(c,"UPDATE appointment SET doctor_id=? WHERE id=?",otherDoctor,appointment));
            change(c,"UPDATE appointment SET status='CANCELLED' WHERE id=?",appointment);
            long replacement=insert(c,"INSERT INTO appointment(appointment_no,patient_id,doctor_id,appointment_time,schedule_id) VALUES (?,?,?,'2099-01-01 09:00:00',?)","MIG_C_"+suffix,patient,doctor,schedule);
            CHECKS.add("Cancellation releases active reservation key");
            long medicine=insert(c,"INSERT INTO medicine(code,name,specification,unit,unit_price) VALUES (?,?,?,?,?)","MIG_MED_"+suffix,"测试药品非真实处方","教学规格","盒",new BigDecimal("12.50"));
            rejected(c,"Negative medicine price rejected",3819,()->change(c,"UPDATE medicine SET unit_price=-1 WHERE id=?",medicine));
            change(c,"INSERT INTO medicine_inventory(medicine_id,stock_quantity) VALUES (?,2)",medicine);
            rejected(c,"Negative inventory rejected",3819,()->change(c,"UPDATE medicine_inventory SET stock_quantity=-1 WHERE medicine_id=?",medicine));
            long prescription=insert(c,"INSERT INTO prescription(prescription_no,appointment_id) VALUES (?,?)","MIG_P_"+suffix,replacement);
            rejected(c,"Second prescription for same visit rejected",1062,()->change(c,"INSERT INTO prescription(prescription_no,appointment_id) VALUES (?,?)","MIG_P2_"+suffix,replacement));
            rejected(c,"Issued prescription requires issue time",3819,()->change(c,"UPDATE prescription SET status='ISSUED' WHERE id=?",prescription));
            rejected(c,"Null required medicine name rejected",1048,()->change(c,"UPDATE medicine SET name=NULL WHERE id=?",medicine));
            rejected(c,"Invalid prescription quantity rejected",3819,()->change(c,"INSERT INTO prescription_item(prescription_id,line_no,medicine_id,quantity,unit_price,instructions) VALUES (?,1,?,0,12.50,'教学测试')",prescription,medicine));
            insert(c,"INSERT INTO prescription_item(prescription_id,line_no,medicine_id,quantity,unit_price,instructions) VALUES (?,1,?,1,12.50,'仅迁移测试，不用于真实诊疗')",prescription,medicine);
            rejected(c,"Referenced medicine deletion rejected",1451,()->change(c,"DELETE FROM medicine WHERE id=?",medicine));
            rejected(c,"Missing medicine reference rejected",1452,()->change(c,"INSERT INTO medicine_inventory(medicine_id,stock_quantity) VALUES (?,0)",Long.MAX_VALUE));
            change(c,"UPDATE medicine_inventory SET stock_quantity=stock_quantity-1,version=version+1 WHERE medicine_id=? AND stock_quantity>=1",medicine);
            insert(c,"INSERT INTO inventory_movement(business_key,medicine_id,change_quantity,balance_after,reason) VALUES (?,?,-1,1,'迁移验证')","MIG_K_"+suffix,medicine);
            rejected(c,"Inventory business idempotency key enforced",1062,()->change(c,"INSERT INTO inventory_movement(business_key,medicine_id,change_quantity,balance_after,reason) VALUES (?,?,-1,0,'重复测试')","MIG_K_"+suffix,medicine));
            if(change(c,"UPDATE medicine_inventory SET stock_quantity=stock_quantity-2,version=version+1 WHERE medicine_id=? AND stock_quantity>=2",medicine)!=0)
                throw new IllegalStateException("Conditional stock deduction failed");
            CHECKS.add("Conditional stock deduction refuses insufficient stock");
            if(change(c,"UPDATE doctor_schedule SET remaining_capacity=remaining_capacity-1,version=version+1 WHERE id=? AND status='OPEN' AND remaining_capacity>0",schedule)!=1)
                throw new IllegalStateException("Conditional schedule deduction failed");
            CHECKS.add("Conditional schedule deduction succeeds with available capacity");
        }finally{c.rollback();c.setAutoCommit(true);}
        CHECKS.add("All verification fixtures rolled back");
    }
    public static void main(String[] args) {
        int completed=0;boolean historyStarted=false;Path output=null;
        try {
            var reader=new BufferedReader(new InputStreamReader(System.in,StandardCharsets.UTF_8));
            String host=field(reader),port=field(reader),database=field(reader),admin=field(reader),password=field(reader);
            String appUser=field(reader);Path source=Path.of(field(reader));output=Path.of(field(reader));Path snapshotPath=Path.of(field(reader));
            if(!Set.of("localhost","127.0.0.1").contains(host)||!port.matches("[0-9]{1,5}")||!database.equals("medical_system")||!appUser.matches("medical_lab_[a-f0-9]{12}"))
                throw new IllegalArgumentException("Unexpected project connection");
            byte[] bytes=Files.readAllBytes(source);
            String checksum=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            String sql=new String(bytes,StandardCharsets.UTF_8).replace("\uFEFF","").replaceAll("(?m)^\\s*--[^\\r\\n]*","");
            var commands=Arrays.stream(sql.split(";")).map(String::strip).filter(s->!s.isEmpty()).toList();
            if(commands.size()!=9)throw new IllegalStateException("Unexpected migration statement count");
            for(String command:commands)if(!command.matches("(?is)^(ALTER TABLE (doctor|patient|appointment)\\s|CREATE TABLE (doctor_schedule|medicine|medicine_inventory|prescription|prescription_item|inventory_movement)\\s).*"))
                throw new IllegalStateException("Migration safety check failed");
            var properties=new Properties();properties.setProperty("user",admin);properties.setProperty("password",password);
            properties.setProperty("characterEncoding","UTF-8");properties.setProperty("connectTimeout","5000");
            properties.setProperty("socketTimeout","30000");
            var original=readSnapshot(snapshotPath);
            Files.createDirectories(output);
            try(var c=DriverManager.getConnection("jdbc:mysql://"+host+":"+port+"/"+database+"?serverTimezone=Asia/Shanghai",properties)){
                if(scalar(c,"SELECT GET_LOCK('medical_system_schema_v002',0)")!=1)throw new IllegalStateException("Another migration holds the lock");
                try{
                    c.setReadOnly(false);
                    assertOriginalRows(c,original);
                    CHECKS.add("All original rows and original column values match the pre-migration snapshot");
                    if(scalar(c,"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='schema_migration'")==0){
                        if(scalar(c,"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()")!=4)
                            throw new IllegalStateException("Unexpected existing schema; automatic migration refused");
                        try(var s=c.createStatement()){s.executeUpdate("CREATE TABLE schema_migration (version VARCHAR(32) PRIMARY KEY, checksum CHAR(64) NOT NULL, status VARCHAR(16) NOT NULL, completed_steps INT NOT NULL DEFAULT 0, applied_at DATETIME NULL, CONSTRAINT chk_migration_status CHECK(status IN ('APPLYING','APPLIED','FAILED'))) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");}
                    }
                    boolean alreadyApplied=false;
                    try(var p=c.prepareStatement("SELECT checksum,status,completed_steps FROM schema_migration WHERE version=?")){
                        p.setString(1,VERSION);try(var rows=p.executeQuery()){if(rows.next()){
                            if(!checksum.equals(rows.getString(1)))throw new IllegalStateException("Migration checksum changed");
                            if(!rows.getString(2).equals("APPLIED"))throw new IllegalStateException("Incomplete prior migration; manual inspection required");
                            alreadyApplied=true;completed=rows.getInt(3);
                        }}
                    }
                    if(!alreadyApplied){
                        if(scalar(c,"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()")!=5)
                            throw new IllegalStateException("Unexpected pre-migration tables");
                        change(c,"INSERT INTO schema_migration(version,checksum,status) VALUES (?,?,'APPLYING')",VERSION,checksum);
                        historyStarted=true;
                        try(var s=c.createStatement()){
                            s.execute("SET SESSION lock_wait_timeout=10");
                            for(String command:commands){s.executeUpdate(command);completed++;change(c,"UPDATE schema_migration SET completed_steps=? WHERE version=?",completed,VERSION);}
                        }
                    }
                    verifyStructure(c);
                    exerciseConstraints(c);
                    assertOriginalRows(c,original);
                    // Grant only DML on new application tables, never schema or global privileges.
                    try(var s=c.createStatement()){
                        for(String name:NEW)s.executeUpdate("GRANT SELECT, INSERT, UPDATE, DELETE ON "+table(database)+"."+table(name)+" TO '"+appUser+"'@'"+host+"'");
                    }
                    if(!alreadyApplied)change(c,"UPDATE schema_migration SET status='APPLIED',applied_at=CURRENT_TIMESTAMP WHERE version=?",VERSION);
                    try(var writer=Files.newBufferedWriter(output.resolve("schema-after.sql"),StandardCharsets.UTF_8);
                        var s=c.createStatement()){
                        for(String name:BASE){try(var rows=s.executeQuery("SHOW CREATE TABLE "+table(name))){rows.next();writer.write(rows.getString(2)+";\n\n");}}
                        for(String name:NEW){try(var rows=s.executeQuery("SHOW CREATE TABLE "+table(name))){rows.next();writer.write(rows.getString(2)+";\n\n");}}
                    }
                    String result="{\"status\":\"success\",\"version\":\"V002\",\"database\":\"medical_system\",\"checksum\":"+json(checksum)
                            +",\"alreadyApplied\":"+alreadyApplied+",\"completedSteps\":"+completed+",\"businessTables\":10,\"historyTables\":1,"
                            +"\"originalDepartmentCount\":"+(original.get("department").size()-1)+",\"originalRowsUnchanged\":true,"
                            +"\"checks\":["+String.join(",",CHECKS.stream().map(UpdateMedicalSchema::json).toList())+"]}";
                    Files.writeString(output.resolve("migration-result.json"),result,StandardCharsets.UTF_8);
                    System.out.println(result);
                }catch(Exception exception){
                    if(historyStarted)change(c,"UPDATE schema_migration SET status='FAILED' WHERE version=?",VERSION);
                    throw exception;
                }finally{try(var s=c.createStatement()){s.executeQuery("SELECT RELEASE_LOCK('medical_system_schema_v002')");}}
            }
        }catch(Exception exception){
            String detail=exception instanceof SQLException e?"SQL "+e.getErrorCode()+" / "+e.getSQLState():exception.getMessage();
            String safe="Migration stopped after "+completed+" DDL steps: "+detail+". No automatic table deletion or rollback was attempted.";
            try{if(output!=null)Files.writeString(output.resolve("migration-failure.txt"),safe,StandardCharsets.UTF_8);}catch(Exception ignored){}
            System.err.println(safe);System.exit(1);
        }
    }
}
