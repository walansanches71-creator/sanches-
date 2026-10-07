package br.com.sanches.controleartes;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;
import java.util.Iterator;

public class LocalDB extends SQLiteOpenHelper {
    static final String NAME="controle_artes.db"; static final int VERSION=5;
    LocalDB(Context c){super(c,NAME,null,VERSION);}
    public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE companies(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT UNIQUE NOT NULL,phone TEXT DEFAULT '')");
        db.execSQL("CREATE TABLE services(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT UNIQUE NOT NULL,description TEXT DEFAULT '',cost REAL DEFAULT 0,price REAL DEFAULT 0)");
        db.execSQL("CREATE TABLE arts(id INTEGER PRIMARY KEY AUTOINCREMENT,company TEXT,phone TEXT,client_id INTEGER DEFAULT 0,service TEXT,description TEXT,price REAL DEFAULT 0,cost REAL DEFAULT 0,status TEXT,photo TEXT,created_at INTEGER,received_at INTEGER DEFAULT 0,due_at INTEGER DEFAULT 0,alerted_at INTEGER DEFAULT 0)");
        db.execSQL("CREATE TABLE expenses(id INTEGER PRIMARY KEY AUTOINCREMENT,description TEXT,amount REAL DEFAULT 0,created_at INTEGER)");
        db.execSQL("CREATE TABLE settings(k TEXT PRIMARY KEY,v TEXT)");
        db.execSQL("INSERT INTO settings(k,v) VALUES('pix','')"); db.execSQL("INSERT INTO settings(k,v) VALUES('pix_name','')");
    }
    public void onUpgrade(SQLiteDatabase db,int oldV,int newV){
        if(oldV<2) db.execSQL("CREATE TABLE IF NOT EXISTS expenses(id INTEGER PRIMARY KEY AUTOINCREMENT,description TEXT,amount REAL DEFAULT 0,created_at INTEGER)");
        if(oldV<3) db.execSQL("ALTER TABLE arts ADD COLUMN client_id INTEGER DEFAULT 0");
        if(oldV<4){ db.execSQL("ALTER TABLE arts ADD COLUMN received_at INTEGER DEFAULT 0"); db.execSQL("ALTER TABLE arts ADD COLUMN due_at INTEGER DEFAULT 0"); db.execSQL("ALTER TABLE arts ADD COLUMN alerted_at INTEGER DEFAULT 0"); }\n        if(oldV<5) db.execSQL("ALTER TABLE services ADD COLUMN description TEXT DEFAULT ''");
    }
    Cursor arts(){return getReadableDatabase().rawQuery("SELECT * FROM arts ORDER BY id DESC",null);}
    Cursor clientArts(String company){return getReadableDatabase().rawQuery("SELECT * FROM arts WHERE company=? ORDER BY id DESC",new String[]{company});}
    Cursor companies(){return getReadableDatabase().rawQuery("SELECT * FROM companies ORDER BY name COLLATE NOCASE",null);}
    Cursor services(){return getReadableDatabase().rawQuery("SELECT * FROM services ORDER BY name COLLATE NOCASE",null);}
    Cursor expenses(){return getReadableDatabase().rawQuery("SELECT * FROM expenses ORDER BY id DESC",null);}
    long addCompany(String name,String phone){ContentValues v=new ContentValues();v.put("name",name.trim());v.put("phone",phone==null?"":phone.trim());return getWritableDatabase().insertWithOnConflict("companies",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
    void deleteCompany(long id){getWritableDatabase().delete("companies","id=?",new String[]{String.valueOf(id)});}
    long addService(String name,double cost,double price){return addService(name,"",cost,price);}\n    long addService(String name,String description,double cost,double price){ContentValues v=new ContentValues();v.put("name",name.trim());v.put("description",description==null?"":description.trim());v.put("cost",cost);v.put("price",price);return getWritableDatabase().insertWithOnConflict("services",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
    void deleteService(long id){getWritableDatabase().delete("services","id=?",new String[]{String.valueOf(id)});}
    long addArt(long clientId,String company,String phone,String service,String desc,double price,double cost,String status,String photo){return addArt(clientId,company,phone,service,desc,price,cost,status,photo,System.currentTimeMillis(),0);}
    long addArt(long clientId,String company,String phone,String service,String desc,double price,double cost,String status,String photo,long receivedAt,long dueAt){ContentValues v=new ContentValues();v.put("client_id",clientId);v.put("company",company);v.put("phone",phone);v.put("service",service);v.put("description",desc);v.put("price",price);v.put("cost",cost);v.put("status",status);v.put("photo",photo);v.put("created_at",System.currentTimeMillis());v.put("received_at",receivedAt);v.put("due_at",dueAt);v.put("alerted_at",0);return getWritableDatabase().insert("arts",null,v);}
    void updateArt(long id,long clientId,String company,String phone,String service,String desc,double price,double cost,String status,String photo){updateArt(id,clientId,company,phone,service,desc,price,cost,status,photo,0,0);}
    void updateArt(long id,long clientId,String company,String phone,String service,String desc,double price,double cost,String status,String photo,long receivedAt,long dueAt){ContentValues v=new ContentValues();v.put("client_id",clientId);v.put("company",company);v.put("phone",phone);v.put("service",service);v.put("description",desc);v.put("price",price);v.put("cost",cost);v.put("status",status);v.put("photo",photo);if(receivedAt>0)v.put("received_at",receivedAt);v.put("due_at",dueAt);v.put("alerted_at",0);getWritableDatabase().update("arts",v,"id=?",new String[]{String.valueOf(id)});}
    void updateStatus(long id,String status){ContentValues v=new ContentValues();v.put("status",status);getWritableDatabase().update("arts",v,"id=?",new String[]{String.valueOf(id)});}
    void deleteArt(long id){getWritableDatabase().delete("arts","id=?",new String[]{String.valueOf(id)});}
    long lastArtId(){Cursor c=getReadableDatabase().rawQuery("SELECT id FROM arts ORDER BY id DESC LIMIT 1",null);try{return c.moveToFirst()?c.getLong(0):0;}finally{c.close();}}
    void markAlerted(long id,long when){ContentValues v=new ContentValues();v.put("alerted_at",when);getWritableDatabase().update("arts",v,"id=?",new String[]{String.valueOf(id)});}
    ArtSnapshot getArtSnapshot(long id){Cursor c=getReadableDatabase().rawQuery("SELECT company,service,description,status,due_at,alerted_at,photo FROM arts WHERE id=?",new String[]{String.valueOf(id)});try{if(!c.moveToFirst())return null;return new ArtSnapshot(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getLong(4),c.getLong(5),c.getString(6));}finally{c.close();}}
    static class ArtSnapshot{String company,service,description,status,photo;long dueAt,alertedAt;ArtSnapshot(String c,String s,String d,String st,long due,long al,String ph){company=c;service=s;description=d;status=st;dueAt=due;alertedAt=al;photo=ph;}}
    long addExpense(String desc,double amount){ContentValues v=new ContentValues();v.put("description",desc);v.put("amount",amount);v.put("created_at",System.currentTimeMillis());return getWritableDatabase().insert("expenses",null,v);}
    void deleteExpense(long id){getWritableDatabase().delete("expenses","id=?",new String[]{String.valueOf(id)});}
    String setting(String key){Cursor c=getReadableDatabase().rawQuery("SELECT v FROM settings WHERE k=?",new String[]{key});try{return c.moveToFirst()?c.getString(0):"";}finally{c.close();}}
    void setting(String key,String value){ContentValues v=new ContentValues();v.put("k",key);v.put("v",value==null?"":value);getWritableDatabase().insertWithOnConflict("settings",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
    JSONObject exportJson(){JSONObject root=new JSONObject();try{root.put("version",3);root.put("created",System.currentTimeMillis());root.put("arts",rows(arts()));root.put("companies",rows(companies()));root.put("services",rows(services()));root.put("expenses",rows(expenses()));JSONObject s=new JSONObject();s.put("pix",setting("pix"));s.put("pix_name",setting("pix_name"));root.put("settings",s);}catch(Exception ignored){}return root;}
    JSONArray rows(Cursor c){JSONArray a=new JSONArray();try{while(c.moveToNext()){JSONObject o=new JSONObject();for(int i=0;i<c.getColumnCount();i++)o.put(c.getColumnName(i),c.getString(i));a.put(o);}}catch(Exception ignored){}finally{c.close();}return a;}
    boolean importJson(JSONObject root){SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{db.delete("arts",null,null);db.delete("companies",null,null);db.delete("services",null,null);db.delete("expenses",null,null);db.delete("settings",null,null);importRows(db,"companies",root.optJSONArray("companies"));importRows(db,"services",root.optJSONArray("services"));importRows(db,"arts",root.optJSONArray("arts"));importRows(db,"expenses",root.optJSONArray("expenses"));JSONObject s=root.optJSONObject("settings");if(s!=null){setting("pix",s.optString("pix"));setting("pix_name",s.optString("pix_name"));}db.setTransactionSuccessful();return true;}catch(Exception e){return false;}finally{db.endTransaction();}}
    void importRows(SQLiteDatabase db,String table,JSONArray arr)throws Exception{if(arr==null)return;for(int i=0;i<arr.length();i++){JSONObject o=arr.getJSONObject(i);ContentValues v=new ContentValues();Iterator<String> it=o.keys();while(it.hasNext()){String k=it.next();if("id".equals(k))continue;v.put(k,o.optString(k));}db.insert(table,null,v);}}
}