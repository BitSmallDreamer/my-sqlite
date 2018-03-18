package my.sqlite.utils;

import my.sqlite.annotation.SqliteColumn;
import my.sqlite.annotation.SqliteID;
import my.sqlite.annotation.SqliteSql;
import my.sqlite.annotation.SqliteTable;
import my.sqlite.annotation.SqliteTransient;
import my.sqlite.base.SqliteBaseEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

/**
 * 实体类T的信息收集器
 * Sqlite的Sql语句生成器
 *
 * @author 欧阳洁
 * @create 2017-09-30 10:56
 **/
public class SqliteSqlHelper<T extends SqliteBaseEntity> {
    private Class<T> targetClass;//实体类
    private String tableName;//表名
    private String idName;//主键名
    private Field idField;//主键变量属性
    List<Field> columnFields;//表列名对应的变量属性集合
    Map<String, String> columnMap;//表列名和字段映射map

    /**
     * 构造函数
     *
     * @param targetClass
     */
    public SqliteSqlHelper(Class<T> targetClass) {
        this.tableName = this.getTableNameForClass(targetClass);
        this.targetClass = targetClass;
        this.columnFields = new Vector<Field>();
        this.columnMap = new HashMap<String, String>();
        this.getColumnFields();//收集表列名对应的变量属性相关信息，包括表列名和字段映射
    }

    /**
     * 创建创建表的sql语句
     *
     * @return
     */
    public String createTableSql() {
        StringBuffer sql = new StringBuffer("create table if not exists ");
        sql.append(this.tableName).append("(");
        boolean useCumma = false;
        for (Field field : this.columnFields) {
            if (useCumma) {
                sql.append(",");//第一次不用逗号
            }else {
                useCumma = true;
            }
            String columnName = field.getName();
            String columnType = "char(20)";
            String notNull = "";
            SqliteID id = field.getAnnotation(SqliteID.class);
            if (id != null) {
                columnName = SqliteUtils.isBlank(id.name()) ? field.getName() : id.name();
                columnType = id.type();
                //主键默认不为空，如果自增长默认自增长
                notNull = id.autoincrement() ? " primary key autoincrement not null" : " primary key not null";
            } else {
                SqliteColumn column = field.getAnnotation(SqliteColumn.class);
                if (null != column) {
                    columnName = SqliteUtils.isBlank(column.name()) ? field.getName() : column.name();
                    columnType = column.type();
                    notNull = column.notNull() ? " not null" : "";
                }
            }
            sql.append(columnName.toLowerCase()).append(" ").append(columnType.toLowerCase()).append(" ").append(notNull);
        }
        sql.append(")");
        return sql.toString();
    }

    /**
     * 创建删除语句
     */
    public void createDelete(T target) {
        List<Object> param = new Vector<Object>();
        StringBuffer sqlBuffer = new StringBuffer();
        sqlBuffer.append("DELETE FROM ").append(this.tableName);
        finishWhereOfAnd(sqlBuffer, param, target);//完成where条件

        target.setCurrentSql(sqlBuffer.toString());
        target.setCurrentParam(param);
    }

    /**
     * 根据Id删除Sql
     *
     * @param id
     */
    public String createDeleteById(Object id) {
        StringBuffer sqlBuffer = new StringBuffer();
        sqlBuffer.append("DELETE FROM ").append(this.tableName).append(" WHERE ");
        sqlBuffer.append(this.idName).append("=?");

        return sqlBuffer.toString();
    }

    /**
     * 创建更新语句
     */
    public void createUpdate(T target) {
        List<Object> param = new Vector<Object>();
        StringBuffer sqlBuffer = new StringBuffer();
        sqlBuffer.append("UPDATE ").append(this.tableName).append(" SET ");
        int count = 0;
        for (Field field : this.columnFields) {
            if (!Modifier.isStatic(field.getModifiers())) {
                Object value = readField(field, target);
                if(null == value){//为空不做更新考虑
                    continue;
                }
                SqliteID id = field.getAnnotation(SqliteID.class);
                if (id == null) {
                    if (count > 0) sqlBuffer.append(",");
                    String columnName = field.getName();
                    SqliteColumn sqliteColumn = field.getAnnotation(SqliteColumn.class);
                    if (null != sqliteColumn) {
                        columnName = SqliteUtils.isBlank(sqliteColumn.name()) ? field.getName() : sqliteColumn.name();
                    }
                    sqlBuffer.append(columnName.toLowerCase()).append("=?");
                    param.add(value);
                    count++;
                }
            }
        }
        finishWhereOfAnd(sqlBuffer, param, target);//完成where条件

        target.setCurrentSql(sqlBuffer.toString());
        target.setCurrentParam(param);
    }

    /**
     * 创建插入语句
     */
    public void createInsert(T target) {
        List<Object> param = new Vector<Object>();
        StringBuffer sqlBuffer = new StringBuffer();
        sqlBuffer.append("INSERT INTO ").append(this.tableName).append("(");
        for (Field field : this.columnFields) {
            if (!Modifier.isStatic(field.getModifiers())) {
                SqliteID id = field.getAnnotation(SqliteID.class);
                if (id == null) {
                    String columnName = field.getName();
                    SqliteColumn sqliteColumn = field.getAnnotation(SqliteColumn.class);
                    if (null != sqliteColumn) {
                        columnName = SqliteUtils.isBlank(sqliteColumn.name()) ? field.getName() : sqliteColumn.name();
                    }
                    sqlBuffer.append(columnName.toLowerCase()).append(",");
                    param.add(readField(field, target));
                }
            }
        }
        int length = sqlBuffer.length();
        sqlBuffer.delete(length - 1, length).append(")values(");
        int size = param.size();
        for (int x = 0; x < size; x++) {
            if (x != 0) {
                sqlBuffer.append(",");
            }
            sqlBuffer.append("?");
        }
        sqlBuffer.append(")");

        target.setCurrentSql(sqlBuffer.toString());
        target.setCurrentParam(param);
    }

    /**
     * 创建查询语句
     */
    public void createSelect(T target) {
        List<Object> param = new Vector<Object>();
        StringBuffer sqlBuffer = new StringBuffer();
        sqlBuffer.append("SELECT * FROM ").append(this.tableName);
        finishWhereOfAnd(sqlBuffer, param, target);

        target.setCurrentSql(sqlBuffer.toString());
        target.setCurrentParam(param);
    }
    /**
     * 创建查询语句
     */
    public void createCount(T target) {
        List<Object> param = new Vector<Object>();
        StringBuffer sqlBuffer = new StringBuffer();
        sqlBuffer.append("SELECT COUNT(1) FROM ").append(this.tableName);
        finishWhereOfAnd(sqlBuffer, param, target);

        target.setCurrentSql(sqlBuffer.toString());
        target.setCurrentParam(param);
    }

    /**
     * 创建查询语句
     */
    public String createSelectById(Object id) {
        StringBuffer sqlBuffer = new StringBuffer();
        sqlBuffer.append("SELECT * FROM ").append(this.tableName).append(" WHERE ");
        sqlBuffer.append(this.idName).append("=?");

        return sqlBuffer.toString();
    }

    /**
     * 创建自定义查询语句
     */
    public void convertSelfSql(StackTraceElement daoMethodInfo, T target) {
        if (null == daoMethodInfo) {//为空用默认的查询语句
            createSelect(target);
            return;
        }
        Method method = getMethod(daoMethodInfo.getClassName(), daoMethodInfo.getMethodName(), target.getClass());
        if (null == method) {//为空用默认的查询语句
            createSelect(target);
            return;
        }
        SqliteSql sqliteSql = method.getAnnotation(SqliteSql.class);
        if (null != sqliteSql) {
            List<Object> param = new Vector<Object>();
            String[] paramNameArr = sqliteSql.params();
            if (null != paramNameArr && paramNameArr.length > 0) {
                Field[] fieldArray = this.targetClass.getDeclaredFields();
                for (String paramName : paramNameArr) {
                    Object value = null;
                    for (Field field : fieldArray) {
                        if (paramName.equalsIgnoreCase(field.getName())) {
                            value = readField(field, target);
                            break;
                        }
                    }
                    param.add(value);
                }

            }
            String sql = SqliteUtils.replace(sqliteSql.sql(), "this.tableName", this.tableName);
            //TODO 此处可以读取自定义SQL的辅助注解，像上面提到的SqliteSqlWhereIf注解，实现动态SQL
            target.setCurrentSql(sql);
            target.setCurrentParam(param);
        }
    }

    /**
     * 创建自定义查询语句，参数随机
     *
     * @param daoMethodInfo
     * @param params
     * @return
     */
    public String convertSelfSql(StackTraceElement daoMethodInfo, Object... params) {
        if (null == daoMethodInfo) {//为空不做处理
            System.out.println("未获取到自定义的语句！");
            return null;
        }
        Class<?>[] classArr = null;
        if (null != params && params.length > 0) {
            classArr = new Class<?>[params.length];
            for (int i = 0; i < params.length; i++) {
                classArr[i] = params[i].getClass();
            }
        }
        Method method = getMethod(daoMethodInfo.getClassName(), daoMethodInfo.getMethodName(), classArr);
        if (null == method) {//为空不做处理
            System.out.println("未获取到自定义的语句！");
            return null;
        }
        SqliteSql sqliteSql = method.getAnnotation(SqliteSql.class);
        String sql = SqliteUtils.replace(sqliteSql.sql(), "this.tableName", this.tableName);
        //TODO 此处可以读取自定义SQL的辅助注解，像上面提到的SqliteSqlWhereIf注解，实现动态SQL
        return sql;
    }

    /**
     * 通过类名、方法名和参数类型定位到具体的方法
     *
     * @param className
     * @param methodName
     * @param parameterTypes
     * @return
     */
    protected Method getMethod(String className, String methodName, Class<?>... parameterTypes) {
        try {
            Class<?> clazz = Class.forName(className);
            Method method = clazz.getMethod(methodName, parameterTypes);
            return method;
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 获取列属性集合（过滤调非表字段属性）
     */
    protected void getColumnFields() {
        Field[] fieldArray = this.targetClass.getDeclaredFields();
        if (null == fieldArray || fieldArray.length == 0) {
            return;
        }
        for (Field field : fieldArray) {
            String columnName = field.getName();
            if (field.isAnnotationPresent(SqliteTransient.class)) {
                // 有SqliteTransient注解的属性不记录，但是值的映射填充可以添加
                SqliteColumn column = field.getAnnotation(SqliteColumn.class);
                if (null != column) {
                    columnName = SqliteUtils.isBlank(column.name()) ? field.getName() : column.name();
                }
                this.columnMap.put(columnName.toLowerCase(), field.getName());
                continue;//如果为非表字段的属性，则不做表字段记录
            }
            this.columnFields.add(field);
            SqliteID id = field.getAnnotation(SqliteID.class);
            if (id != null) {
                columnName = SqliteUtils.isBlank(id.name()) ? field.getName() : id.name();
                this.idName = columnName.toLowerCase();
                this.idField = field;
            } else {
                SqliteColumn column = field.getAnnotation(SqliteColumn.class);
                if (null != column) {
                    columnName = SqliteUtils.isBlank(column.name()) ? field.getName() : column.name();
                }
            }
            this.columnMap.put(columnName.toLowerCase(), field.getName());
        }
        //getColumnFields(clazz.getSuperclass());
    }

    /**
     * 根据注解获取表名
     */
    public String getTableName() {
        if (SqliteUtils.isBlank(this.tableName)) {
            this.tableName = this.getTableNameForClass(this.targetClass);
        }
        return this.tableName;
    }

    /**
     * 获取注解表名
     *
     * @param clazz
     * @return
     */
    public String getTableNameForClass(Class<T> clazz) {
        if (!SqliteUtils.isBlank(this.tableName)) {
            return this.tableName.toLowerCase();
        }
        SqliteTable table = clazz.getAnnotation(SqliteTable.class);
        if (null != table) {
            this.tableName = table.name();
            if (SqliteUtils.isBlank(this.tableName)) {
                this.tableName = clazz.getSimpleName();
            }
        } else {
            this.tableName = clazz.getSimpleName();
        }
        return this.tableName.toLowerCase();
    }

    /**
     * 读取属性的值
     *
     * @param field
     * @return
     */
    protected Object readField(Field field, T target) {
        try {
            return SqliteUtils.readField(field, target, true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 补全用and连接的sql语句
     *
     * @param sqlBuffer
     * @param param
     * @param target
     */
    private void finishWhereOfAnd(StringBuffer sqlBuffer, List<Object> param, T target) {
        sqlBuffer.append(" WHERE 1=1 ");
        Object idValue = null;
        if (null != this.idField) {
            idValue = readField(this.idField, target);
        }
        if (idValue != null) {
            sqlBuffer.append(" and ").append(this.idName).append("=?");
            param.add(idValue);
        } else {
            for (Field field : this.columnFields) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    Object currentValue = readField(field, target);
                    if (null != currentValue && !SqliteUtils.equals(this.idName, field.getName())) {
                        String columnName = field.getName();
                        SqliteColumn sqliteColumn = field.getAnnotation(SqliteColumn.class);
                        if (null != sqliteColumn) {
                            columnName = SqliteUtils.isBlank(sqliteColumn.name()) ? field.getName() : sqliteColumn.name();
                        }
                        sqlBuffer.append(" and ").append(columnName.toLowerCase()).append("=?");
                        param.add(currentValue);
                    }
                }
            }
        }
    }

    public Class<T> getTargetClass() {
        return targetClass;
    }

    public void setTargetClass(Class<T> targetClass) {
        this.targetClass = targetClass;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getIdName() {
        return idName;
    }

    public void setIdName(String idName) {
        this.idName = idName;
    }

    public Field getIdField() {
        return idField;
    }

    public void setIdField(Field idField) {
        this.idField = idField;
    }

    public void setColumnFields(List<Field> columnFields) {
        this.columnFields = columnFields;
    }

    public Map<String, String> getColumnMap() {
        return columnMap;
    }

    public void setColumnMap(Map<String, String> columnMap) {
        this.columnMap = columnMap;
    }
}

