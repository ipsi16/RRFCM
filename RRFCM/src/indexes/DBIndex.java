package indexes;

import java.util.ArrayList;

import clustering.rrfcm.Cluster;
import clustering.rrfcm.DataPoint;

public class DBIndex 
{
	public ArrayList<Cluster> clusters;
	public static final int p = 2;
	public static final int q = 2;
	public float[] similarity;
	public float[][] r;
	

	public DBIndex(ArrayList<Cluster> clusters)
	{
		this.clusters = clusters;
		similarity = new float[clusters.size()];
		r = new float[clusters.size()][clusters.size()];
	}
	
	public float returnIndex()
	{
		float index = 0.0f;
		
		calculateSimilarity();
		calculateRij();
		
		for(int i=0;i<clusters.size();i++)
		{
			index += maxRij(i);
		}
		index = index/clusters.size();
		
		return index;
		
	}
	
	private void calculateSimilarity()
	{
		for(int i=0;i<clusters.size();i++)
		{
			Cluster currCluster = clusters.get(i);
			for(int j=0;j<currCluster.memberDataPoints.size();j++)
			{
				similarity[i] += Math.pow(DataPoint.distanceBetween(currCluster.memberDataPoints.get(j),currCluster.centroid) , q);
			}
			similarity[i]=similarity[i]/currCluster.memberDataPoints.size();
			similarity[i]= (float) Math.pow(similarity[i], 1/q);
		}
	}
	
	private void calculateRij()
	{
		for(int i=0;i<clusters.size();i++)
		{
			for(int j=i+1;j<clusters.size();j++)
			{
				r[i][j]=(similarity[i]+similarity[j])/DataPoint.distanceBetween(clusters.get(i).centroid,clusters.get(j).centroid);
				r[j][i]=r[i][j];
			}
		}
	}
	
	private float maxRij(int clusterIndex)
	{
		int maxPos=0;
		for(int i=0; i<clusters.size();i++)
		{
			if(r[clusterIndex][i]>r[clusterIndex][maxPos])
				maxPos = i;
		}
		
		return r[clusterIndex][maxPos]; 
	}
}
