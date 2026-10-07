package br.com.sanches.controleartes;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.util.*;

public class LocalDB extends SQLiteOpenHelper {
    static final String NAME="controle_artes.db";
    static final int VERSION=1;
    LocalDB(Context c){super(c,NAME,null,VERSION);}

    public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE companies(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT UNIQUE NOT NULL,phone TEXT DEFAULT '')");
        db.execSQL("CREATE TABLE services(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT UNIQUE NOT NULL,cost REAL DEFAULT 0,price REAL DEFAULT 0)");
        db.execSQL("CREATE TABLE arts(id INTEGER PRIMARY KEY AUTOINCREMENT,company TEXT,phone TEXT,service TEXT,description TEXT,price REAL DEFAULT 0,cost REAL DEFAULT 0,status TEXT,photo TEXT,created_at INTEGER)");
        db.execSQL("CREATE TABLE settings(k TEXT PRIMARY KEY,v TEXT)");
        db.execSQL("INSERT INTO settings(k,v) VALUES('pix','')");
        db.execSQL("INSERT INTO settings(k,v) VALUES('pix_name','')");
    }
    public void onUpgrade(SQLiteDatabase db,int oldV,int newV){}
    Cursor arts(){return getReadableDatabase().rawQuery("SELECT * FROM arts ORDER BY id DESC",null);}
    Cursor companies(){return getReadableDatabase().rawQuery("SELECT * FROM companies ORDER BY name COLLATE NOCASE",null);}
    Cursor services(){return getReadableDatabase().rawQuery("SELECT * FROM services ORDER BY name COLLATE NOCASE",null);}
    long addCompany(String name,String phone){
        ContentValues v=new ContentValues();v.put("name",name.trim());v.put("phone",phone==null?"":phone.trim());
        return getWritableDatabase().insertWithOnConflict("companies",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }
    void deleteCompany(long id){getWritableDatabase().delete("companies","id=?",new String[]{String.valueOf(id)});}
    long addService(String name,double cost,double price){
        ContentValues v=new ContentValues();v.put("name",name.trim());v.put("cost",cost);v.put("price",price);
        return getWritableDatabase().insertWithOnConflict("services",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }
    void deleteService(long id){getWritableDatabase().delete("services","id=?",new String[]{String.valueOf(id)});}
    long addArt(String company,String phone,String service,String desc,double price,double cost,String status,String photo){
        ContentValues v=new ContentValues();v.put("company",company);v.put("phone",phone);v.put("service",service);v.put("description",desc);
        v.put("price",price);v.put("cost",cost);v.put("status",status);v.put("photo",photo);v.put("created_at",System.currentTimeMillis());
        return getWritableDatabase().insert("arts",null,v);
    }
    void updateArt(long id,String company,String phone,String service,String desc,double price,double cost,String status,String photo){
        ContentValues v=new ContentValues();v.put("company",company);v.put("phone",phone);v.put("service",service);v.put("description",desc);
        v.put("price",price);v.put("cost",cost);v.put("status",status);v.put("photo",photo);
        getWritableDatabase().update("arts",v,"id=?",new String[]{String.valueOf(id)});
    }
    void updateStatus(long id,String status){ContentValues v=new ContentValues();v.put("status",status);getWritableDatabase().update("arts",v,"id=?",new String[]{String.valueOf(id)});}
    void deleteArt(long id){getWritableDatabase().delete("arts","id=?",new String[]{String.valueOf(id)});}
    String setting(String key){Cursor c=getReadableDatabase().rawQuery("SELECT v FROM settings WHERE k=?",new String[]{key});try{return c.moveToFirst()?c.getString(0):"";}finally{c.close();}}
    void setting(String key,String value){ContentValues v=new ContentValues();v.put("k",key);v.put("v",value==null?"":value);getWritableDatabase().insertWithOnConflict("settings",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
}