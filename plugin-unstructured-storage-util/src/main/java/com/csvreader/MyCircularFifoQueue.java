package com.csvreader;

import org.apache.commons.collections4.queue.CircularFifoQueue;
import java.util.Iterator;

/**
 * {@code @Classname} MyCircularFifoQueue
 * {@code @Description} 定义定长队列，该队列满足先进先出，当队列满了之后，插入数据会覆盖最早插入的数据，并提供元素转字符串功能
 * {@code @Date} 2022/9/10 23:09
 * {@code @Created} by xiaolin
 */


public class MyCircularFifoQueue {

    // 定义环形队列
    private CircularFifoQueue<Character> circularFifoQueue;
    private final StringBuilder stringBuilder = new StringBuilder();

    // 环形队列初始化，通过分隔符长度来初始化队列长度
    public MyCircularFifoQueue(int i){
        circularFifoQueue = new CircularFifoQueue<>(i);
    }

    // 存元素
    public void offer(Character value){
        circularFifoQueue.offer(value);
    }

    // 队列元素转字符串
    @Override
    public String toString(){
        stringBuilder.setLength(0);
        Iterator<Character> iterator = circularFifoQueue.iterator();
        while (iterator.hasNext()){
            stringBuilder.append(iterator.next());
        }
        return stringBuilder.toString();
    }
}
