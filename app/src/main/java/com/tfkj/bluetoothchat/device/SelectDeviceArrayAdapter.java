package com.tfkj.bluetoothchat.device;

import android.content.Context;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.TextView;

import com.tfkj.bluetoothchat.R;
import com.tfkj.bluetoothchat.bean.MessageBean;
import com.tfkj.bluetoothchat.bean.SelectDeviceBean;

import java.util.ArrayList;
import java.util.List;

public class SelectDeviceArrayAdapter extends ArrayAdapter<SelectDeviceBean> {
    private ArrayList<SelectDeviceBean> selectDeviceBeans;
    private boolean selectAll;

    public SelectDeviceArrayAdapter(@NonNull Context context, @LayoutRes int resource, ArrayList<SelectDeviceBean> selectDeviceBeans) {
        super(context, resource, selectDeviceBeans);
        this.selectDeviceBeans = selectDeviceBeans;
    }

    @Override
    public
    @NonNull
    View getView(int position, View convertView, @NonNull ViewGroup parent) {
        View view;
        ViewHolder viewHolder;
        if (convertView == null) {
            view = LayoutInflater.from(this.getContext()).inflate(R.layout.item_select_device, parent, false);
            viewHolder = new ViewHolder();
            viewHolder.tvDevice = view.findViewById(R.id.tv_device);
            viewHolder.checkBox =  view.findViewById(R.id.checkbox_device);
            view.setTag(viewHolder);
        } else {
            view = convertView;
            viewHolder = (ViewHolder) view.getTag();
        }

        SelectDeviceBean selectDeviceBean = selectDeviceBeans.get(position);
        viewHolder.tvDevice.setText(selectDeviceBean.getDeviceName() + "\n" + selectDeviceBean.getDeviceAddress());
        viewHolder.checkBox.setChecked(selectDeviceBean.isSelected());

        return view;
    }

    public List<SelectDeviceBean> getData() {
        return selectDeviceBeans;
    }

    public void add(SelectDeviceBean selectDeviceBean) {
        selectDeviceBeans.add(selectDeviceBean);
        this.notifyDataSetChanged();
    }

    public void selectAll(){
        selectAll = !selectAll;
        for (int i = 0; i < selectDeviceBeans.size(); i++) {
            selectDeviceBeans.get(i).setSelected(selectAll);
        }
        this.notifyDataSetChanged();
    }

    private static class ViewHolder {
        TextView tvDevice;
        CheckBox checkBox;
    }
}
