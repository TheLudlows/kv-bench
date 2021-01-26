import io.four.NanoBench;

import java.util.ArrayList;
import java.util.List;

public class TestForNoneBench {
    public static void main(String[] args) {
        NanoBench bench = NanoBench.create();
        bench.measure("new_string", () -> {
            List<String> list = new ArrayList();
            for (int i = 0; i < 10000; i++) {
                list.add(i + "");
            }
        });
    }
}
