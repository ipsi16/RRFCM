package clustering.rrfcm;

import indexes.DBIndex;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;

import clustering.rrfcm.Cluster;
import clustering.rrfcm.DataPoint;

public class Rrfcm 
{
	public static final int n = 500;
	public static final int noOfClusters = 5;
	public static final float epsolon = 0.5f;
	public static final float tao =1.5f;
	public static final float wlower = 0.7f;
	public static final float wupper = 1 - wlower;
	public static final int m = 2;	
	public static ArrayList<Cluster> clusters = new ArrayList<Cluster>();
	public static ArrayList<DataPoint> datapoints = new ArrayList<DataPoint>();
	public static ArrayList<DataPoint> orgDatapoints = new ArrayList<DataPoint>();
	public static float[][] distance = new float[n][noOfClusters];
	public static float[][] membership = new float[noOfClusters][n];
	public static float[][] oldMembership = new float[noOfClusters][n];
	public static float[][] copyDistance = new float[n][noOfClusters];
	public static int[][] pointCount = new int[noOfClusters][3];
	
	public static void main(String[] args)
	{
		//read i/p from file
		try
		{
			fetchData();
			
		}
		catch (NumberFormatException e) {
			System.err.println("Invalid number entries in the file\nFile should only contain comma seperated numbers");
			return;
		}
		if(datapoints.size()<noOfClusters)
		{
			System.out.println("Insufficient points");
			return;
		}
		
		//normalize datapoints
		normalise();
		
		//allocate cluster centroids			
		for(int i=0;i < noOfClusters; i++)
		{
			Cluster c = new Cluster(datapoints.get(i));
			clusters.add(c);
		}		
		
		//calculate membership value of each point for each clusters
		calculateDistance();
		
		//allocate each point to upper or lower approx of the respective clusters
		allocateClusters();
		
		while(!stopSignal())
		{
			determineNewCentroid();
			calculateDistance();
			allocateClusters();
		}
		
		//Final Output
        System.out.println("Cluster Output : \n");
        /*for(int i=0;i<noOfClusters;i++)
        {
             System.out.print("Cluster "+(i+1)+" : ");
             for(int j=0;j<orgDatapoints.size();j++)
             {
                    if(membership[i][j]!=0)
                    {
                       System.out.print(membership[i][j]*100+"% of "+orgDatapoints.get(j).point+"\t");
                            
                     }        
                  
               }
               System.out.println();
         }
         System.out.println();
	*/
         System.out.println("Cluster  Low   Upper   Total");
         for(int i=0;i<noOfClusters;i++)
         {
                    System.out.println((i+1)+"\t"+pointCount[i][0]+"\t"+ pointCount[i][1]+"\t"+pointCount[i][2]);
                   
         }
                  
         //DB Index
         DBIndex dbindex = new DBIndex(clusters);
         System.out.println(dbindex.returnIndex());
	}
	
	
	private static void fetchData() throws NumberFormatException
	{		
		try
		{
			FileReader freader = new FileReader("Data.txt");
			BufferedReader breader = new BufferedReader(freader);	
			String dataLine = breader.readLine();
			while(dataLine!=null)
			{
				
				ArrayList<Float> dim = new ArrayList<Float>();
				
				String[] dimString = dataLine.split(",");				
				for (String string : dimString)
				{
					string = string.trim();
					dim.add(Float.parseFloat(string));					
				}
				DataPoint datapoint = new DataPoint(dim);
				datapoints.add(datapoint);
				orgDatapoints.add(DataPoint.copyDataPoint(datapoint));
				dataLine = breader.readLine();
				
			}
			breader.close();
		}
		catch(NumberFormatException e)
		{
			throw e;
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		
	}
	
	private static void calculateDistance()
	{
              
		for(int i=0; i<datapoints.size() ; i++)
		{
			for(int j=0;j<noOfClusters;j++)
			{
				distance[i][j] = DataPoint.distanceBetween(datapoints.get(i),clusters.get(j).centroid );
			}
		}
	}
	
	private static void allocateClusters()
	{		
		//initialise clusters
		for(Cluster cluster : clusters)
		{
			cluster.lowerApprox = new ArrayList<DataPoint>();
			cluster.upperApprox = new ArrayList<DataPoint>();			
			cluster.memberDataPoints = new ArrayList<DataPoint>();
		}
		
		calculateMembership();
		
		//Create a copy of distance matrix for modification needed to be done on it for the algo
		for(int i=0; i < datapoints.size(); i++)
		{
			for(int j=0; j<noOfClusters;j++)
			{
				copyDistance[i][j]= distance[i][j];
			}
		}
		
		//allocate at least one point to each cluster on the basis of the closest datapoint to each cluster,ie, min distance => max  membership
		for(int k=0; k < noOfClusters; k++)
		{	
			//finding <cluster,datapoint> pair with minimum distance
			int minDPPos=0,minClusterPos=0;
			for(int i=0; i < datapoints.size(); i++)
			{
				for(int j=0; j<noOfClusters;j++)
				{
					if(copyDistance[i][j]<copyDistance[minDPPos][minClusterPos])
					{
						minDPPos=i;	minClusterPos=j;
					}
				}
			}
			
			//adding corresponding datapoint to lower approx (& upper approx) of corresponding cluster
			clusters.get(minClusterPos).lowerApprox.add(datapoints.get(minDPPos));
			clusters.get(minClusterPos).upperApprox.add(datapoints.get(minDPPos));
			clusters.get(minClusterPos).memberDataPoints.add(datapoints.get(minDPPos));
			membership[minClusterPos][minDPPos]=1.0f;
			
			//removing the corresponding datapoint for consideration
			for(int i=0; i<noOfClusters;i++)
			{
				copyDistance[minDPPos][i]=1000.0f;
				if(i!=minClusterPos)
					membership[i][minDPPos]=0.0f;
			}
			//removing the corresponding cluster for consideration
			for(int i=0; i<datapoints.size();i++)
			{
				copyDistance[i][minClusterPos]=1000.0f;				
			}
		}
		
		//allocating rest of the datapoints to the clusters
		for(int i=0;i<datapoints.size();i++)
		{
			if(!isAllocated(i))			//ie, if that datapoint has not been allocated already to the lower approx of some cluster
			{
				int min2Pos,minPos=0;
		        try
		        {
					//finding closest jth cluster for ith point
					for(int j=0;j<noOfClusters;j++)
					{
						if(distance[i][j]<distance[i][minPos])
							minPos = j;
					}
					
					//finding 2nd closest cluster for ith point
					if(minPos==0)
					 min2Pos = 1;
					else min2Pos =0;
					for(int j=0;j<noOfClusters;j++)
					{
						if(j!=minPos && distance[i][j]<distance[i][min2Pos])
							min2Pos = j;
					}
					
					if(distance[i][min2Pos]/distance[i][minPos]>tao)
					{
						clusters.get(minPos).lowerApprox.add(datapoints.get(i));			//adding ith datapoint to lower approx (& upper approx) of cluster with min distance from the point
						clusters.get(minPos).upperApprox.add(datapoints.get(i));
						clusters.get(minPos).memberDataPoints.add(datapoints.get(i));
						membership[minPos][i]=1.0f;
						for(int k=0; k<noOfClusters;k++)
						{
							if(k!=minPos)
								membership[k][i]=0.0f;
						}
					}
					else
					{
						clusters.get(minPos).upperApprox.add(datapoints.get(i));			//adding ith datapoint to upper approx of cluster with highest and 2nd highest membership
						clusters.get(min2Pos).upperApprox.add(datapoints.get(i));
						clusters.get(minPos).memberDataPoints.add(datapoints.get(i));			
						clusters.get(min2Pos).memberDataPoints.add(datapoints.get(i));
					}
		          }
		          catch(ArrayIndexOutOfBoundsException ex)
		          {
		                System.out.println("i="+i+"minPos="+minPos);
		          }
			}
		}
	}
	
	private static void determineNewCentroid()
	{
        System.out.println("Cluster Centroids: \n");
		for(int i=0;i<noOfClusters;i++)
		{
			Cluster currCluster = clusters.get(i);
			
			ArrayList<Float> lowerApproxComponent = new ArrayList<Float>();
			ArrayList<Float> upperApproxComponent = new ArrayList<Float>();
            for(int k=0;k<datapoints.get(0).point.size();k++)
		    {
                    lowerApproxComponent.add(0.0f);
                    upperApproxComponent.add(0.0f);
			}
            
            int lowerApproxCount=0,uc=0;
            float upperApproxCount=0.0f;
			for(int j=0;j<datapoints.size();j++)
			{				
				if(membership[i][j]>0.0f && membership[i][j]<=1.0f)
				{
					if(membership[i][j]==1.0f)
					{
						lowerApproxCount++;
						upperApproxCount++;
						for(int k=0;k<datapoints.get(j).point.size();k++)
						{
							lowerApproxComponent.set(k, lowerApproxComponent.get(k)+datapoints.get(j).point.get(k));
							upperApproxComponent.set(k, upperApproxComponent.get(k)+datapoints.get(j).point.get(k));
						}
					}
					else
					{
						upperApproxCount+=(float)Math.pow(membership[i][j], m);
						for(int k=0;k<datapoints.get(j).point.size();k++)
						{
							upperApproxComponent.set(k, upperApproxComponent.get(k)+(float)Math.pow(membership[i][j], m)*datapoints.get(j).point.get(k));
						}
					}
				}
			}
			
			pointCount[i][0]= currCluster.lowerApprox.size();
            pointCount[i][1]= currCluster.upperApprox.size()-currCluster.lowerApprox.size();
            pointCount[i][2]=currCluster.upperApprox.size();
            
            
			for(int k=0;k<datapoints.get(0).point.size();k++)
			{
				currCluster.centroid.point.set(k,0.0f);	
			}	
			for(int k = 0;k<currCluster.centroid.point.size();k++)
			{
				currCluster.centroid.point.set(k,wlower*lowerApproxComponent.get(k)/lowerApproxCount+wupper*upperApproxComponent.get(k)/upperApproxCount);
			}  
			System.out.println(currCluster.centroid.point);
        }
        System.out.println();
    }
	
	private static void calculateMembership()
	{
		for(int i=0; i < noOfClusters; i++)
		{
			for(int j=0; j<datapoints.size();j++)
			{
				oldMembership[i][j]= membership[i][j];
			}
		}
	
		
		for(int i = 0; i < noOfClusters; i++)
		{
			for(int j=0; j<datapoints.size();j++)
			{
				membership[i][j]=-1;
			}
		}
				
		for(int i = 0; i < noOfClusters; i++)
		{
			for(int j=0; j<datapoints.size();j++)
			{
				float dij = DataPoint.distanceBetween(clusters.get(i).centroid,datapoints.get(j) );
				
				if(dij==0.0f)
				{
					membership[i][j]= 1;
					for(int h=0;h<noOfClusters;h++)
					{
						if(h!=i)
						{	
							membership[h][j]=0; 
						}
					}
				}
				else if(membership[i][j]==-1) 
				{
					membership[i][j]=0;
					for(int k = 0; k < noOfClusters; k++)
					{
						membership[i][j]+=Math.pow((dij/DataPoint.distanceBetween(datapoints.get(j),clusters.get(k).centroid)), 2.0/(m-1));
						
					}
                    if(membership[i][j]!=0)
					membership[i][j] = 1/membership[i][j];
				}
				
			}
		}        
	}
	
	private static boolean stopSignal()
	{
		for(int i=0;i<noOfClusters;i++)
		{
			for(int j=0;j<datapoints.size();j++)
			{
				if(Math.abs(oldMembership[i][j]-membership[i][j])>epsolon)
					return false;
			}
		}
		return true;
	}
	
	private static boolean isAllocated(int DPIndex)
	{
		for(int i=0;i<noOfClusters;i++)
		{
			if(membership[i][DPIndex]==1.0f)
				return true;
		}
		return false;
	}
	
	public static void normalise()				//standard normalization between range 0 - 1
	{
		//arrays that store max and min value for every ith dimension
		float[] max,min;
		max = new float[datapoints.get(0).point.size()];
		min = new float[datapoints.get(0).point.size()];
		
		//initialising max and min array to dimensions of first datapoint
		for(int i = 0; i<datapoints.get(0).point.size();i++)
		{
			max[i] = datapoints.get(0).point.get(i).floatValue(); 
			min[i] = datapoints.get(0).point.get(i).floatValue(); 
		}
			
		//finding max and min values for each dimension 
		for(DataPoint dp : datapoints)
		{
			ArrayList<Float> currPoint = dp.point;
			for(int i =0 ; i<currPoint.size();i++)
			{
				if(currPoint.get(i)>max[i])
				{
					max[i]=currPoint.get(i);
				}
				else if(currPoint.get(i)<min[i])
				{
					min[i]=currPoint.get(i);
				}
			}			
		}
		
		//applying normalization formula new value = (oldValue - oldMinVal)/(oldMaxVal - oldMinVal)
		for(DataPoint dp : datapoints)
		{
			ArrayList<Float> currPoint = dp.point;
			for(int i =0 ; i<currPoint.size();i++)
			{
				currPoint.set(i, (currPoint.get(i)-min[i])/(max[i]-min[i]));
			}
		}
	}

}
