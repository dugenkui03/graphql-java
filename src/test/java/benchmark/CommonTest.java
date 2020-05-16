package benchmark;

import graphql.language.Document;
import graphql.parser.Parser;

public class CommonTest {
    public static void main(String[] args) {
        String dsl="query($userId:Long){\n" +
                "  wmVIPNewUser{\n" +
                "    newUserWithDetail(userId:$userId){\n" +
                "      # 使用多个字段进行计算\n" +
                "      userType @fieldCal(exp : \"userType+(isNewUser?1000000:2000000)\")\n" +
                "      originalValue : userType \n" +
                "      isNewUser @fieldCal (exp : \"false\")\n" +
                "      noNewDimensionSet @fieldCal (exp : \"seq.list(\\\"dugenkui\\\",\\\"UUUID\\\",\\\"UUUSerID\\\")\")\n" +
                "    }\n" +
                "  }\n" +
                "}";

        Parser parser=new Parser();
        Document document = parser.parseDocument(dsl);

        System.out.println(document);
    }
}
