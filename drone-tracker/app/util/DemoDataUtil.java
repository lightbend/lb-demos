package util;

import java.util.ArrayList;

public class DemoDataUtil {
    public static ArrayList<String> generateOrgNames(int numOrgs) {
        ArrayList<String> orgNames = orgNames = new ArrayList<>();
        for (int i = 0; i < numOrgs; i++) {
            orgNames.add(String.format("Org %d", i));
        }
        return orgNames;
    }
}
