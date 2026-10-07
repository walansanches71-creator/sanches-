package br.com.sanches.controleartes;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;
import java.util.Iterator;

public class LocalDB extends SQLiteOpenHelper {
    static final String NAME="controle_artes.db"; static final int VERSION=2;
    LocalDB(Context c){super(c,NAME,null,VERSION);}
    public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE companies(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT UNIQUE NOT NULL,phone TEXT DEFAULT '')");
        db.execSQL("CREATE TABLE services(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT UNIQUE NOT NULL,cost REAL DEFAULT 0,price REAL DEFAULT 0)");
        db.execSQL("CREATE TABLE arts(id INTEGER PRIMARY KEY AUTOINCREMENT,company TEXT,phone TEXT,service TEXT,description TEXT,price REAL DEFAULT 0,cost REAL DEFAULT 0,status TEXT,photo TEXT,created_at INTEGER)");
        db.execSQL("CREATE TABLE expenses(id INTEGER PRIMARY KEY AUTOINCREMENT,description TEXT,amount REAL DEFAULT 0,created_at INTEGER)");
        db.execSQL("CREATE TABLE settings(k TEXT PRIMARY KEY,v TEXT)");
        db.execSQL("INSERT INTO settings(k,v) VALUES('pix','')"); db.execSQL("INSERT INTO settings(k,v) VALUES('pix_name','')");
    }
    public void onUpgrade(SQLiteDatabase db,int oldV,int newV){if(oldV<2)db.execSQL("CREATE TABLE IF NOT EXISTS expenses(id INTEGER PRIMARY KEY AUTOINCREMENT,description TEXT,amount REAL DEFAULT 0,created_at INTEGER)");}
    Cursor arts(){return getReadableDatabase().rawQuery("SELECT * FROM arts ORDER BY id DESC",null);}
    Cursor companies(){return getReadableDatabase().rawQuery("SELECT * FROM companies ORDER BY name COLLATE NOCASE",null);}
    Cursor services(){return getReadableDatabase().rawQuery("SELECT * FROM services ORDER BY name COLLATE NOCASE",null);}
    Cursor expenses(){return getReadableDatabase().rawQuery("SELECT * FROM expenses ORDER BY id DESC",null);}
    long addCompany(String name,String phone){ContentValues v=new ContentValues();v.put("name",name.trim());v.put("phone",phone==null?"":phone.trim());return getWritableDatabase().insertWithOnConflict("companies",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
    void deleteCompany(long id){getWritableDatabase().delete("companies","id=?",new String[]{String.valueOf(id)});}
    long addService(String name,double cost,double price){ContentValues v=new ContentValues();v.put("name",name.trim());v.put("cost",cost);v.put("price",price);return getWritableDatabase().insertWithOnConflict("services",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
    void deleteService(long id){getWritableDatabase().delete("services","id=?",new String[]{String.valueOf(id)});}
    long addArt(String company,String phone,String service,String desc,double price,double cost,String status,String photo){ContentValues v=new ContentValues();v.put("company",company);v.put("phone",phone);v.put("service",service);v.put("description",desc);v.put("price",price);v.put("cost",cost);v.put("status",status);v.put("photo",photo);v.put("created_at",System.currentTimeMillis());return getWritableDatabase().insert("arts",null,v);}
    void updateArt(long id,String company,String phone,String service,String desc,double price,double cost,String status,String photo){ContentValues v=new ContentValues();v.put("company",company);v.put("phone",phone);v.put("service",service);v.put("description",desc);v.put("price",price);v.put("cost",cost);v.put("status",status);v.put("photo",photo);getWritableDatabase().update("arts",v,"id=?",new String[]{String.valueOf(id)});}
    void updateStatus(long id,String status){ContentValues v=new ContentValues();v.put("status",status);getWritableDatabase().update("arts",v,"id=?",new String[]{String.valueOf(id)});}
    void deleteArt(long id){getWritableDatabase().delete("arts","id=?",new String[]{String.valueOf(id)});}
    long addExpense(String desc,double amount){ContentValues v=new ContentValues();v.put("description",desc);v.put("amount",amount);v.put("created_at",System.currentTimeMillis());return getWritableDatabase().insert("expenses",null,v);}
    void deleteExpense(long id){getWritableDatabase().delete("expenses","id=?",new String[]{String.valueOf(id)});}
    String setting(String key){Cursor c=getReadableDatabase().rawQuery("SELECT v FROM settings WHERE k=?",new String[]{key});try{return c.moveToFirst()?c.getString(0):"";}finally{c.close();}}
    void setting(String key,String value){ContentValues v=new ContentValues();v.put("k",key);v.put("v",value==null?"":value);getWritableDatabase().insertWithOnConflict("settings",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
    JSONObject exportJson(){
        JSONObject root=new JSONObject();try{
            root.put("version",2);root.put("created",System.currentTimeMillis());
            JSONArray[] arrays={rows(arts()),rows(companies()),rows(services()),rows(expenses())};
            root.put("arts",arrays[0]);root.put("companies",arrays[1]);root.put("services",arrays[2]);root.put("expenses",arrays[3]);
            JSONObject settings=new JSONObject();settings.put("pix",setting("pix"));settings.put("pix_name",setting("pix_name"));root.put("settings",settings);
        }catch(Exception ignored){}return root;
    }
    JSONArray rows(Cursor c){JSONArray a=new JSONArray();try{while(c.moveToNext()){JSONObject o=new JSONObject();for(int i=0;i<c.getColumnCount();i++)o.put(c.getColumnName(i),c.getString(i));a.put(o);}}catch(Exception ignored){}finally{c.close();}return a;}
    boolean importJson(JSONObject root){SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
        db.delete("arts",null,null);db.delete("companies",null,null);db.delete("services",null,null);db.delete("expenses",null,null);db.delete("settings",null,null);
        importRows(db,"companies",root.optJSONArray("companies"));importRows(db,"services",root.optJSONArray("services"));importRows(db,"arts",root.optJSONArray("arts"));importRows(db,"expenses",root.optJSONArray("expenses"));
        JSONObject s=root.optJSONObject("settings");if(s!=null){setting("pix",s.optString("pix"));setting("pix_name",s.optString("pix_name"));}
        db.setTransactionSuccessful();return true;
    }catch(Exception e){return false;}finally{db.endTransaction();}}
    void importRows(SQLiteDatabase db,String table,JSONArray arr)throws Exception{if(arr==null)return;for(int i=0;i<arr.length();i++){JSONObject o=arr.getJSONObject(i);ContentValues v=new ContentValues();Iterator<String> it=o.keys();while(it.hasNext()){String k=it.next();if("id".equals(k))continue;v.put(k,o.optString(k));}db.insert(table,null,v);}}
}