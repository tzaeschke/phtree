/*
 * Copyright 2009-2016 Tilmann Zaeschke. All rights reserved.
 * 
 * This file is part of ZooDB.
 * 
 * ZooDB is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * ZooDB is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with ZooDB.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * See the README and COPYING files for further information. 
 */
package ch.ethz.globis.pht.util;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryManagerMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanServer;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;

public class JmxTools {

	private static final MBeanServer SERVER = ManagementFactory.getPlatformMBeanServer();
	private static final String[] POOL_NAMES;
	static {
		List<MemoryPoolMXBean> pList = ManagementFactory.getMemoryPoolMXBeans();
		POOL_NAMES = new String[pList.size()];
		int i = 0;
		for (MemoryPoolMXBean b: pList) {
			POOL_NAMES[i++] = b.getName();
		}
	}
	
	private static long totalDiff;
	private static long totalTime;
	
	public static void reset() {
		totalDiff = 0;
		totalTime = 0;
	}
	
	public static long getDiff() {
		return totalDiff;
	}
	
	public static long getTime() {
		return totalTime;
	}
	
	public static void gc() {
		long td1, td2;
		long tt1, tt2;
		long ts1, ts2;
		int n=0, nMin = 2;
		do {
			sleep();
			td1 = totalDiff;
			tt1 = totalTime;
			ts1 = System.currentTimeMillis();
			System.gc();
			ts2 = System.currentTimeMillis();
			sleep();
			td2 = totalDiff;
			tt2 = totalTime;
			System.out.println("GC: " + (td1/1024/1024) + "MB -> " + (td2/1024/1024) + "MB  " +
			"tGC=" + (tt2-tt1) + "  tSys=" + (ts2-ts1));
			n++;
			//Ignore Eden collection of <3MB
		} while ((Math.abs(td1-td2) >3*1024*1024 || n < nMin) && n < 5);
	}
	
	private static void sleep() {
		try {
			Thread.sleep(100);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
	
	public static void startUp() {
		NotificationListener l = new NotificationListener() {
			@Override
			public void handleNotification(Notification notification, Object handback) {
				GarbageCollectorMXBean b = (GarbageCollectorMXBean) handback;
//				System.out.println("NF: " +
//						"   sn: " + notification.getSequenceNumber() +
//						"   ts: " + notification.getTimeStamp() +
//						"   type: " + notification.getType() +
//						"   msg: " + notification.getMessage() + 
//						"   gc: " + b.getCollectionCount() + "/" + b.getCollectionTime()
//						);
				processNotification(b.getObjectName());
			}
		};
		
		for (GarbageCollectorMXBean b: ManagementFactory.getGarbageCollectorMXBeans()) {
//			System.out.println("GC-Bean: " + b.getName() +  "   c=" + b.getCollectionCount() + "  t=" + b.getCollectionTime());
			try {
				SERVER.addNotificationListener(b.getObjectName(), l, null, b);
			} catch (InstanceNotFoundException e) {
				throw new RuntimeException(e);
			}
		}
		
		gc();
		reset();
	}
	
	
	private static class MemInfo {
		final int gcThreadCount;
		final long duration;
		final long id;
		final long startTime;
		final long endTime;
		long diff;
		
		public MemInfo(int gcThreadCount, long duration, long id, long startTime, long endTime) {
			this.gcThreadCount = gcThreadCount;
			this.duration = duration;
			this.id = id;
			this.startTime = startTime;
			this.endTime = endTime;
		}

		public void addDiff(long l) {
			diff += l;
		}
		
		@Override
		public String toString() {
			return "threads=" + gcThreadCount + "  id=" + id + 
					"  dur=" + duration + "  diff=" + diff;
		}
	}
	
	private static void processNotification(ObjectName name) {
		CompositeData cd;
		try {
			cd = (CompositeData) SERVER.getAttribute(name, "LastGcInfo");
		} catch (AttributeNotFoundException | InstanceNotFoundException
				| MBeanException | ReflectionException e) {
			e.printStackTrace();
			throw new RuntimeException();
		}
		
		//System.out.println("cds: " + cd.getClass().getName() + " " + cd.getCompositeType());
		//CompositeDataSupport cds = new CompositeDataSupport(cd.getCompositeType(), cd); 
//		String[] keys = {"GcThreadCount", "duration", "endTime", "id", 
//				"memoryUsageAfterGc", "memoryUsageBeforeGc", "startTime"};

		String[] keys = {"GcThreadCount", "duration", "id", "startTime", "endTime"};
		Object[] mix = cd.getAll(keys);
		MemInfo mi = new MemInfo((int)mix[0], (long)mix[1], (long)mix[2], (Long)mix[3], (Long)mix[4]);

		
		String KEY_BEFORE = "memoryUsageBeforeGc";
		String KEY_AFTER = "memoryUsageAfterGc";
		
		TabularData tdBefore = (TabularData) cd.get(KEY_BEFORE);
		TabularData tdAfter = (TabularData) cd.get(KEY_AFTER);

		for (String mpName: POOL_NAMES) {
			CompositeData cdBefore = tdBefore.get(new Object[]{mpName});
			MemoryUsage muBefore = MemoryUsage.from((CompositeData) cdBefore.get("value"));
			CompositeData cdAfter = tdAfter.get(new Object[]{mpName});
			MemoryUsage muAfter = MemoryUsage.from((CompositeData) cdAfter.get("value"));
//			System.out.println("MP-diff: " + mpName + " / " + name + 
//					"   init: " + (muAfter.getInit()) + " " + (muBefore.getInit()) +  
//					"   used: " + (muAfter.getUsed()) + " " + (muBefore.getUsed()) +
//					"   committed: " + (muAfter.getCommitted()) + " " + (muBefore.getCommitted()) + 
//					"   max: " + (muAfter.getMax()) + " " + (muBefore.getMax())
//					);
			long diff = muAfter.getUsed()-muBefore.getUsed(); 

			mi.addDiff(diff);
			//System.out.println("MP-diff: " + mpName + " " + diff);
		}
		
//		if (mi.diff > 0) {
//			System.err.println("WARNING: diff=" + mi.diff);
//		}

		totalDiff += mi.diff;
		totalTime += mi.duration;
	}
	
	public static void gcCheck() {
		try {
			MBeanServer server = ManagementFactory.getPlatformMBeanServer();
			Set<ObjectName> names = server.queryNames(null, null);
			for (ObjectName name : names) {
				if (name.toString().contains("GarbageCollector")) {
					System.out.println(name);

					MBeanInfo info = server.getMBeanInfo(name);
					for (MBeanAttributeInfo ai: info.getAttributes()) {
						System.out.println("AI: " + ai.getName() + "  --  " + ai.getDescription());
					}
					for (MBeanNotificationInfo ai: info.getNotifications()) {
						System.out.println("NI: " + ai.getName() + "  --  " + ai.getDescription());
					}
					for (MBeanOperationInfo ai: info.getOperations()) {
						System.out.println("OI: " + ai.getName() + "  --  " + ai.getDescription());
					}
					System.out.println("ColC: " + server.getAttribute(name, "CollectionCount"));
					System.out.println("ColT: " + server.getAttribute(name, "CollectionTime"));
					System.out.println("LGCI: " + server.getAttribute(name, "LastGcInfo"));
					CompositeData cd = (CompositeData) server.getAttribute(name, "LastGcInfo");
					System.out.println("cds: " + cd.getClass().getName() + " " + cd.getCompositeType());
					//CompositeDataSupport cds = new CompositeDataSupport(cd.getCompositeType(), cd); 
					String[] keys = {"GcThreadCount", "duration", "endTime", "id", 
							"memoryUsageAfterGc", "memoryUsageBeforeGc", "startTime"};
					for (String s1: keys) {
						Object o = cd.get(s1);
						if (!(o instanceof TabularData)) {
							System.out.println(" " + s1 + " -> " + o);
						}
						if (o instanceof TabularData) {
							TabularData td = (TabularData) o;
							System.out.println("TD: " + s1);
							//System.out.println("TD: " + td.getClass().getName() + " " + td.getTabularType());
							Collection<CompositeData> c = (Collection<CompositeData>) td.values();
							for (CompositeData cd2: c) {
								//String[] key2 = {"key", "value"};
								String[] key3 = {"committed", "init", "max", "used"};
								//System.out.println("cd2: " + cd2.getCompositeType());
//								for (String s2: key2) {
//									System.out.println("222- " + s2 + " -> " + cd2.get(s2));
//								}
								System.out.println("222- \"key\" -> " + cd2.get("key"));
								for (String s3: key3) {
									CompositeData cd3 = (CompositeData) cd2.get("value");
									System.out.println("         " + s3 + " -> " + cd3.get(s3));
								}
							}
						}
					}
					//GcInfoCompositeData gcd;
					//LazyCompositeData l;
					
					for (Object v: cd.values()) {
						System.out.println("v=("+v.getClass().getName()+")" + v);
//						if (v instanceof TabularData) {
//							TabularData t = (TabularData) v;
//							for (Object k: t.keySet()) {
//								//print(t, k, "-");
//							}
//						}
					}
				}
			}
			
			for (GarbageCollectorMXBean b: ManagementFactory.getGarbageCollectorMXBeans()) {
				System.out.println("GC-Bean: " + b.getName() +  "   c=" + b.getCollectionCount() + "  t=" + b.getCollectionTime());
			}
			for (MemoryManagerMXBean b: ManagementFactory.getMemoryManagerMXBeans()) {
				System.out.println("MM-Bean: " + b.getName());
				for (String n: b.getMemoryPoolNames()) {
					
				}
			}
			MemoryMXBean mb = ManagementFactory.getMemoryMXBean();
			System.out.println("M-Bean: pfc=" + mb.getObjectPendingFinalizationCount() + 
					"  heap=" + mb.getHeapMemoryUsage() + "  non-heap=" + mb.getNonHeapMemoryUsage());
			for (MemoryPoolMXBean b: ManagementFactory.getMemoryPoolMXBeans()) {
				System.out.println("MP-Bean: " + b.getName() + "  " + b.getType() + "  peak=" + b.getPeakUsage());
				for (String n: b.getMemoryManagerNames()) {
					System.out.println("MP-MM: " + n);
				}
			}
			
//			
//	        StdOutWriter sw = new StdOutWriter();
//	         
//	         Query q = new Query();
//	         q.setObj("java.lang:type=Memory");
//	         q.addAttr("HeapMemoryUsage");
//	         q.addAttr("NonHeapMemoryUsage");
//	        q.addOutputWriter(sw);
//	         server.addQuery(q);
//	 
//	         Query q2 = new Query("java.lang:type=Threading");
//	         q2.addAttr("DaemonThreadCount");
//	         q2.addAttr("PeakThreadCount");
//	         q2.addAttr("ThreadCount");
//	         q2.addOutputWriter(sw);
//	         server.addQuery(q2);
//	 
//	         Query q3 = new Query();
//	         q3.addAttr("LastGcInfo");
//	         q3.addKey("memoryUsageAfterGc");
//	         q3.addKey("memoryUsageBeforeGc");
//	         q3.addKey("committed");
//	         q3.addKey("init");
//	         q3.addKey("max");
//	         q3.addKey("used");
//	        q3.addOutputWriter(sw);
//	        server.addQuery(q3);
//	
//	        Query q4 = new Query();
//	        q4.setObj("java.lang:name=ParNew,type=GarbageCollector");
//	        q4.addAttr("LastGcInfo");
//	        q4.addKey("memoryUsageAfterGc");
//	        q4.addKey("memoryUsageBeforeGc");
//	        q4.addKey("committed");
//	        q4.addKey("init");
//	        q4.addKey("max");
//	        q4.addKey("used");
//	        q4.addOutputWriter(sw);
//	         server.addQuery(q4);
		} catch (InstanceNotFoundException | ReflectionException e) {
			throw new RuntimeException(e);
		} catch (IntrospectionException e) {
			throw new RuntimeException(e);
		} catch (AttributeNotFoundException e) {
			throw new RuntimeException(e);
		} catch (MBeanException e) {
			throw new RuntimeException(e);
		}
	}
	
	private static void print(TabularData td, Object o, String prefix) {
		if (prefix.length() > 10) {
			System.out.println("ABORTING!");
			return;
		}
		prefix += " ";
		if (o instanceof String) {
			System.out.println(prefix + o);
			print(td, td.get(new Object[]{o}), prefix + "+");
			return;
		}
		if (o instanceof CompositeData) {
			CompositeData cd = (CompositeData) o;
			System.out.println(prefix + "CD: " + cd.getCompositeType());
			for (Object v: cd.values()) {
				print(td, v, prefix);
			}
			return;
		}
		if (o instanceof Collection) {
			System.out.println(prefix + "Collection: " + o.getClass().getName());
			for (Object v: (Collection<?>)o) {
				print(td, v, prefix);
			}
			return;
		}
		if (o instanceof Long) {
			System.out.println(prefix + "Long: " + o);
			return;
		}
		System.out.println("Not supported: " + o.getClass());
	}

}
