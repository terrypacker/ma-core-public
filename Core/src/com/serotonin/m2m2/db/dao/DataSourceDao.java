/*
    Copyright (C) 2006-2011 Serotonin Software Technologies Inc. All rights reserved.
    @author Matthew Lohbihler
 */
package com.serotonin.m2m2.db.dao;

import java.io.InputStream;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;

import com.serotonin.ShouldNeverHappenException;
import com.serotonin.db.spring.ExtendedJdbcTemplate;
import com.serotonin.m2m2.Common;
import com.serotonin.m2m2.module.ModuleRegistry;
import com.serotonin.m2m2.rt.event.type.AuditEventType;
import com.serotonin.m2m2.rt.event.type.EventType;
import com.serotonin.m2m2.vo.DataPointVO;
import com.serotonin.m2m2.vo.dataSource.DataSourceVO;
import com.serotonin.m2m2.vo.event.EventInstanceVO;
import com.serotonin.m2m2.vo.event.PointEventDetectorVO;
import com.serotonin.util.SerializationHelper;

public class DataSourceDao extends AbstractDao<DataSourceVO<?>> {
    
	static final Log LOG = LogFactory.getLog(DataSourceDao.class);
    private static final String DATA_SOURCE_SELECT = "select id, xid, name, dataSourceType, data from dataSources ";

    public static final DataSourceDao instance = new DataSourceDao();
    
    public DataSourceDao(){
    	super(AuditEventType.TYPE_DATA_SOURCE);
    }
    
    
    public List<DataSourceVO<?>> getDataSources() {
        List<DataSourceVO<?>> dss = query(DATA_SOURCE_SELECT, new DataSourceExtractor());
        Collections.sort(dss, new DataSourceNameComparator());
        return dss;
    }

    public List<DataSourceVO<?>> getDataSourcesForType(String type) {
        return query(DATA_SOURCE_SELECT + "WHERE dataSourceType=?", new Object[] { type }, new DataSourceExtractor());
    }

    static class DataSourceNameComparator implements Comparator<DataSourceVO<?>> {
        @Override
        public int compare(DataSourceVO<?> ds1, DataSourceVO<?> ds2) {
            if (StringUtils.isBlank(ds1.getName()))
                return -1;
            return ds1.getName().compareToIgnoreCase(ds2.getName());
        }
    }

    public DataSourceVO<?> getDataSource(int id) {
        return queryForObject(DATA_SOURCE_SELECT + " where id=?", new Object[] { id }, new DataSourceRowMapper(), null);
    }

    public DataSourceVO<?> getDataSource(String xid) {
        return queryForObject(DATA_SOURCE_SELECT + " where xid=?", new Object[] { xid }, new DataSourceRowMapper(),
                null);
    }

    class DataSourceExtractor implements ResultSetExtractor<List<DataSourceVO<?>>> {
        @Override
        public List<DataSourceVO<?>> extractData(ResultSet rs) throws SQLException, DataAccessException {
            DataSourceRowMapper rowMapper = new DataSourceRowMapper();
            List<DataSourceVO<?>> results = new ArrayList<DataSourceVO<?>>();
            int rowNum = 0;
            while (rs.next()) {
                try {
                    results.add(rowMapper.mapRow(rs, rowNum++));
                }
                catch (ShouldNeverHappenException e) {
                    // If the module was removed but there are still records in the database, this exception will be
                    // thrown. Check the inner exception to confirm.
                    if (e.getCause() instanceof ObjectStreamException) {
                        // Yep. Log the occurrence and continue.
                        LOG.error(
                                "Data source with type '" + rs.getString("dataSourceType") + "' and name '"
                                        + rs.getString("name") + "' could not be loaded. Is its module missing?", e);
                    }
                }
            }
            return results;
        }
    }

    class DataSourceRowMapper implements RowMapper<DataSourceVO<?>> {
        @Override
        public DataSourceVO<?> mapRow(ResultSet rs, int rowNum) throws SQLException {
            DataSourceVO<?> ds = (DataSourceVO<?>) SerializationHelper.readObjectInContext(rs.getBinaryStream(5));
            ds.setId(rs.getInt(1));
            ds.setXid(rs.getString(2));
            ds.setName(rs.getString(3));
            ds.setDefinition(ModuleRegistry.getDataSourceDefinition(rs.getString(4)));
            return ds;
        }
    }

    public String generateUniqueXid() {
        return generateUniqueXid(DataSourceVO.XID_PREFIX, "dataSources");
    }

    public boolean isXidUnique(String xid, int excludeId) {
        return isXidUnique(xid, excludeId, "dataSources");
    }

    public void saveDataSource(final DataSourceVO<?> vo) {
        // Decide whether to insert or update.
        if (vo.getId() == Common.NEW_ID)
            insertDataSource(vo);
        else
            updateDataSource(vo);
    }

    private void insertDataSource(final DataSourceVO<?> vo) {
        vo.setId(doInsert("insert into dataSources (xid, name, dataSourceType, data) values (?,?,?,?)",
                new Object[] { vo.getXid(), vo.getName(), vo.getDefinition().getDataSourceTypeName(),
                        SerializationHelper.writeObject(vo) }, new int[] { Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
                        Types.BINARY }));

        AuditEventType.raiseAddedEvent(AuditEventType.TYPE_DATA_SOURCE, vo);
    }

    @SuppressWarnings("unchecked")
    private void updateDataSource(final DataSourceVO<?> vo) {
    	super.save(vo);
        //DataSourceVO<?> old = getDataSource(vo.getId());
        
        //_updateDataSource(vo);
        //AuditEventType.raiseChangedEvent(AuditEventType.TYPE_DATA_SOURCE, old, (ChangeComparable<DataSourceVO<?>>) vo);
    }

    public void _updateDataSource(DataSourceVO<?> vo) {
        ejt.update("update dataSources set xid=?, name=?, dataSourceType=?, data=? where id=?",
                new Object[] { vo.getXid(), vo.getName(), vo.getDefinition().getDataSourceTypeName(),
                        SerializationHelper.writeObject(vo), vo.getId() }, new int[] { Types.VARCHAR, Types.VARCHAR,
                        Types.VARCHAR, Types.BINARY, Types.INTEGER });
    }

    public void deleteDataSource(final int dataSourceId) {
        DataSourceVO<?> vo = getDataSource(dataSourceId);
        final ExtendedJdbcTemplate ejt2 = ejt;

        new DataPointDao().deleteDataPoints(dataSourceId);

        if (vo != null) {
            getTransactionTemplate().execute(new TransactionCallbackWithoutResult() {
                @Override
                protected void doInTransactionWithoutResult(TransactionStatus status) {
                    ejt2.update("delete from eventHandlers where eventTypeName=? and eventTypeRef1=?", new Object[] {
                            EventType.EventTypeNames.DATA_SOURCE, dataSourceId });
                    ejt2.update("delete from dataSourceUsers where dataSourceId=?", new Object[] { dataSourceId });
                    ejt2.update("delete from dataSources where id=?", new Object[] { dataSourceId });
                }
            });

            AuditEventType.raiseDeletedEvent(AuditEventType.TYPE_DATA_SOURCE, vo);
        }
    }

    public void deleteDataSourceType(final String dataSourceType) {
        List<Integer> dsids = queryForList("select id from dataSources where dataSourceType=?",
                new Object[] { dataSourceType }, Integer.class);
        for (Integer dsid : dsids)
            deleteDataSource(dsid);
    }

    public void copyPermissions(final int fromDataSourceId, final int toDataSourceId) {
        final List<Integer> userIds = queryForList("select userId from dataSourceUsers where dataSourceId=?",
                new Object[] { fromDataSourceId }, Integer.class);

        ejt.batchUpdate("insert into dataSourceUsers values (?,?)", new BatchPreparedStatementSetter() {
            @Override
            public int getBatchSize() {
                return userIds.size();
            }

            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                ps.setInt(1, toDataSourceId);
                ps.setInt(2, userIds.get(i));
            }
        });
    }



    
    //TODO See how this works, with the Device name situation
    @Override
	public int copy(int existingId, String newXid, String newName) {
		return this.copyDataSource(existingId, newName,newXid, newName+"Device");
	}


	public int copyDataSource(final int dataSourceId, final String name, final String xid, final String deviceName) {
        return getTransactionTemplate().execute(new TransactionCallback<Integer>() {
            @Override
            public Integer doInTransaction(TransactionStatus status) {
                DataPointDao dataPointDao = new DataPointDao();

                DataSourceVO<?> dataSource = getDataSource(dataSourceId);
                // Copy the data source.
                DataSourceVO<?> dataSourceCopy = dataSource.copy();
                dataSourceCopy.setId(Common.NEW_ID);
                dataSourceCopy.setXid(xid);
                dataSourceCopy.setEnabled(false);
                dataSourceCopy.setName(name);
                saveDataSource(dataSourceCopy);

                // Copy permissions.
                copyPermissions(dataSource.getId(), dataSourceCopy.getId());

                // Copy the points.
                for (DataPointVO dataPoint : dataPointDao.getDataPoints(dataSourceId, null)) {
                    DataPointVO dataPointCopy = dataPoint.copy();
                    dataPointCopy.setId(Common.NEW_ID);
                    dataPointCopy.setXid(dataPointDao.generateUniqueXid());
                    dataPointCopy.setName(dataPoint.getName());
                    dataPointCopy.setDeviceName(deviceName);
                    dataPointCopy.setDataSourceId(dataSourceCopy.getId());
                    dataPointCopy.setEnabled(dataPoint.isEnabled());
                    dataPointCopy.getComments().clear();

                    // Copy the event detectors
                    for (PointEventDetectorVO ped : dataPointCopy.getEventDetectors()) {
                        ped.setId(Common.NEW_ID);
                        ped.njbSetDataPoint(dataPointCopy);
                    }

                    dataPointDao.saveDataPoint(dataPointCopy);

                    // Copy permissions
                    dataPointDao.copyPermissions(dataPoint.getId(), dataPointCopy.getId());
                }

                return dataSourceCopy.getId();
            }
        });
    }

    public Object getPersistentData(int id) {
        return query("select rtdata from dataSources where id=?", new Object[] { id },
                new ResultSetExtractor<Serializable>() {
                    @Override
                    public Serializable extractData(ResultSet rs) throws SQLException, DataAccessException {
                        if (!rs.next())
                            return null;

                        InputStream in = rs.getBinaryStream(1);
                        if (in == null)
                            return null;

                        return (Serializable) SerializationHelper.readObjectInContext(in);

                        //                        Blob blob = rs.getBlob(1);
                        //                        if (blob == null)
                        //                            return null;
                        //
                        //                        return (Serializable) SerializationHelper.readObjectInContext(blob.getBinaryStream());
                    }
                });
    }

    public void savePersistentData(int id, Object data) {
        ejt.update("update dataSources set rtdata=? where id=?", new Object[] { SerializationHelper.writeObject(data),
                id }, new int[] { Types.BINARY, Types.INTEGER });
    }


	/* (non-Javadoc)
	 * @see com.serotonin.m2m2.db.dao.AbstractDao#getTableName()
	 */
	@Override
	protected String getTableName() {
		return SchemaDefinition.DATASOURCES_TABLE;
	}


	/* (non-Javadoc)
	 * @see com.serotonin.m2m2.db.dao.AbstractDao#getXidPrefix()
	 */
	@Override
	protected String getXidPrefix() {
		return DataSourceVO.XID_PREFIX;
	}


	/* (non-Javadoc)
	 * @see com.serotonin.m2m2.db.dao.AbstractDao#voToObjectArray(com.serotonin.m2m2.vo.AbstractVO)
	 */
	@Override
	protected Object[] voToObjectArray(DataSourceVO<?> vo) {
		return new Object[] { 
				vo.getXid(), 
				vo.getName(), 
				vo.getDefinition().getDataSourceTypeName(),
                SerializationHelper.writeObject(vo)};
	}


	/* (non-Javadoc)
	 * @see com.serotonin.m2m2.db.dao.AbstractDao#getNewVo()
	 */
	@Override
	public DataSourceVO<?> getNewVo() {
		throw new ShouldNeverHappenException("Unable to create generic data source, must supply a type");
	}


	/* (non-Javadoc)
	 * @see com.serotonin.m2m2.db.dao.AbstractBasicDao#getProperties()
	 */
	@Override
	protected List<String> getProperties() {
		return Arrays.asList(
                "id",
                "xid",
                "name",
                "dataSourceType",
                "data"
                );
	}
	@Override
	protected Integer getIndexType(){
		return Types.INTEGER;
	}
	@Override
	protected List<Integer> getPropertyTypes(){
		return Arrays.asList(
					Types.VARCHAR,
					Types.VARCHAR,
					Types.VARCHAR,
					Types.BLOB
				);		
	}
	@Override
	protected Map<String, Comparator<DataSourceVO<?>>> getComparatorMap() {
		HashMap<String,Comparator<DataSourceVO<?>>> comparatorMap = new HashMap<String,Comparator<DataSourceVO<?>>>();
		
		comparatorMap.put("typeDescriptionString", new Comparator<DataSourceVO<?>>(){
			public int compare(DataSourceVO<?> lhs, DataSourceVO<?>rhs){
				return lhs.getTypeDescriptionString().compareTo(rhs.getTypeDescriptionString());
			}
		});
		
		comparatorMap.put("connectionDescriptionString", new Comparator<DataSourceVO<?>>(){
			public int compare(DataSourceVO<?> lhs, DataSourceVO<?>rhs){
				return lhs.getConnectionDescriptionString().compareTo(rhs.getConnectionDescriptionString());
			}
		});
		
		return comparatorMap;
	}

	@Override
	protected Map<String, IFilter<DataSourceVO<?>>> getFilterMap(){
		HashMap<String, IFilter<DataSourceVO<?>>> filterMap = new HashMap<String,IFilter<DataSourceVO<?>>>();
		
		filterMap.put("typeDescriptionString", new IFilter<DataSourceVO<?>>(){
			
			private String regex;
			@Override
			public boolean filter(DataSourceVO<?> vo) {
				return !vo.getTypeDescriptionString().matches(regex);
			}

			@Override
			public void setFilter(Object matches) {
				this.regex = "(?i)"+(String)matches;
				
			}
			
		});
		
		filterMap.put("connectionDescriptionString", new IFilter<DataSourceVO<?>>(){
			private String regex;
			@Override
			public boolean filter(DataSourceVO<?> vo) {
				return !vo.getConnectionDescriptionString().matches(regex);
			}

			@Override
			public void setFilter(Object matches) {
				this.regex = "(?i)"+(String)matches; //Make case insensitive like DB
				
				
			}
			
		});		
		
		return filterMap;
	}
	
	
	
	
	

	/* (non-Javadoc)
	 * @see com.serotonin.m2m2.db.dao.AbstractBasicDao#getPropertiesMap()
	 */
	@Override
	protected Map<String, String> getPropertiesMap() {
		return new HashMap<String,String>();
	}


	/* (non-Javadoc)
	 * @see com.serotonin.m2m2.db.dao.AbstractBasicDao#getRowMapper()
	 */
	@Override
	public RowMapper<DataSourceVO<?>> getRowMapper() {
		return new DataSourceRowMapper();
	}



}
