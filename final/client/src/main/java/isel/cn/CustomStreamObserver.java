package isel.cn;

import io.grpc.stub.StreamObserver;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CustomStreamObserver<T> implements StreamObserver<T> {

    /** For Blocks **/
    String name;
    FileOutputStream out;

    /** For an Identifier or Result **/
    T result;

    /** For image names **/
    List<String> resImageNames = new ArrayList<>();

    boolean isCompleted=false;

    @Override
    public void onNext(T reply) {
        if(reply instanceof ImageNames){
            resImageNames.addAll(((ImageNames)reply).getNamesList());
        } else if(reply instanceof Blocks) {
            name = ((Blocks) reply).getImageName();
            try {
                if(out == null) {
                    System.out.println("name = "+name);
                    out = new FileOutputStream(name);
                }
                out.write(((Blocks) reply).getBlock().toByteArray());
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            result = reply;
        }
        isCompleted = true;
    }

    @Override
    public void onError(Throwable throwable) {
        if(out != null) {
            try {
                out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        System.out.println("Error on call:" + throwable.getMessage());
        isCompleted = true;
    }

    @Override
    public void onCompleted() {
        if(out != null) {
            try {
                out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        System.out.println("stream completed");
        isCompleted = true;
    }
}
