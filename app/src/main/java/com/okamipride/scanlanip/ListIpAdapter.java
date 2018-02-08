package com.okamipride.scanlanip;

import java.util.List;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

public class ListIpAdapter extends BaseAdapter {

	List<Device> list = null;
	
	public ListIpAdapter (List<Device> iplist) {
		list = iplist;
	}
	
	@Override
	public int getCount() {
		return (list == null) ? 0 : (list.size());
	}

	@Override
	public Object getItem(int position) {
		return (list == null) ? null : list.get(position);
	}

	@Override
	public long getItemId(int position) {
		return 0;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder itemLayout = null;
        if (convertView == null) {
	            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
	            convertView = inflater.inflate(R.layout.item_view, parent, false);
            itemLayout = new ViewHolder();
            itemLayout.tvIP = (TextView) convertView.findViewById(R.id.tv_ip);
            itemLayout.tvMAC = (TextView) convertView.findViewById(R.id.tv_mac);
            convertView.setTag(itemLayout);
	     } else {
            itemLayout = (ViewHolder) convertView.getTag();
        }
		 
		 if (list != null) {
             Device device = list.get(position);
			 if (device != null) {
                 itemLayout.tvIP.setText(device.getIp());
                 itemLayout.tvMAC.setText(device.getMac());
             } else {
                 itemLayout.tvIP.setText("");
                 itemLayout.tvMAC.setText("");
			 } 		
		 } else {
             itemLayout.tvIP.setText("");
             itemLayout.tvMAC.setText("");
		 }		 
		return convertView;
	}

    class ViewHolder {
        TextView tvIP;
        TextView tvMAC;
    }
}
