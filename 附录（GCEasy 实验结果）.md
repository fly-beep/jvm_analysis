# 附录（GCEasy 实验结果）

首先来看 G1 垃圾收集的结果，我直接给出 gceasy 对结果的分析：

1. g1gc_4g_100_1m.log
   
    首先可以看看 JVM 的内存划分：
    
    ![Untitled](pic-gceasy/Untitled.png)
    
    其次可以看看在 G1 GC 的一些关键性能指标，如吞吐量，延迟等：
    
    ![Untitled](pic-gceasy/Untitled%201.png)
    
    其次可以看看在 G1 GC 中每个阶段花费的时间：
    
    ![Untitled](pic-gceasy/Untitled%202.png)
    
    然后可以看看 G1 GC 的过程中，STW 和并发执行的时间消耗：
    
    ![Untitled](pic-gceasy/Untitled%203.png)
    
    最后可以看下导致 G1 GC 的原因：
    
    ![Untitled](pic-gceasy/Untitled%204.png)
    
2. g1gc_4g_200_1m.log
   
    首先可以看看 JVM 的内存划分：
    
    ![Untitled](pic-gceasy/Untitled%205.png)
    
    其次可以看看在 G1 GC 的一些关键性能指标，如吞吐量，延迟等：
    
    ![Untitled](pic-gceasy/Untitled%206.png)
    
    其次可以看看在 G1 GC 中每个阶段花费的时间：
    
    ![Untitled](pic-gceasy/Untitled%207.png)
    
    然后可以看看 G1 GC 的过程中，STW 和并发执行的时间消耗：
    
    ![Untitled](pic-gceasy/Untitled%208.png)
    
    最后可以看下导致 G1 GC 的原因：
    
    ![Untitled](pic-gceasy/Untitled%209.png)
    
3. g1gc_4g_500_1m.log
   
    首先可以看看 JVM 的内存划分：
    
    ![Untitled](pic-gceasy/Untitled%2010.png)
    
    其次可以看看在 G1 GC 的一些关键性能指标，如吞吐量，延迟等：
    
    ![Untitled](pic-gceasy/Untitled%2011.png)
    
    其次可以看看在 G1 GC 中每个阶段花费的时间：
    
    ![Untitled](pic-gceasy/Untitled%2012.png)
    
    然后可以看看 G1 GC 的过程中，STW 和并发执行的时间消耗：
    
    ![Untitled](pic-gceasy/Untitled%2013.png)
    
    最后可以看下导致 G1 GC 的原因：
    
    ![Untitled](pic-gceasy/Untitled%2014.png)
    
4. g1gc_4g_100_2m.log
   
    首先可以看看 JVM 的内存划分：
    
    ![Untitled](pic-gceasy/Untitled%2015.png)
    
    其次可以看看在 G1 GC 的一些关键性能指标，如吞吐量，延迟等：
    
    ![Untitled](pic-gceasy/Untitled%2016.png)
    
    其次可以看看在 G1 GC 中每个阶段花费的时间：
    
    ![Untitled](pic-gceasy/Untitled%2017.png)
    
    然后可以看看 G1 GC 的过程中，STW 和并发执行的时间消耗：
    
    ![Untitled](pic-gceasy/Untitled%2018.png)
    
    最后可以看下导致 G1 GC 的原因：
    
    ![Untitled](pic-gceasy/Untitled%2019.png)
    
5. g1gc_4g_200_2m.log
   
    首先可以看看 JVM 的内存划分：
    
    ![Untitled](pic-gceasy/Untitled%2020.png)
    
    其次可以看看在 G1 GC 的一些关键性能指标，如吞吐量，延迟等：
    
    ![Untitled](pic-gceasy/Untitled%2021.png)
    
    其次可以看看在 G1 GC 中每个阶段花费的时间：
    
    ![Untitled](pic-gceasy/Untitled%2022.png)
    
    然后可以看看 G1 GC 的过程中，STW 和并发执行的时间消耗：
    
    ![Untitled](pic-gceasy/Untitled%2023.png)
    
    最后可以看下导致 G1 GC 的原因：
    
    ![Untitled](pic-gceasy/Untitled%2024.png)
    
6. g1gc_4g_500_2m.log
   
    首先可以看看 JVM 的内存划分：
    
    ![Untitled](pic-gceasy/Untitled%2025.png)
    
    其次可以看看在 G1 GC 的一些关键性能指标，如吞吐量，延迟等：
    
    ![Untitled](pic-gceasy/Untitled%2026.png)
    
    其次可以看看在 G1 GC 中每个阶段花费的时间：
    
    ![Untitled](pic-gceasy/Untitled%2027.png)
    
    然后可以看看 G1 GC 的过程中，STW 和并发执行的时间消耗：
    
    ![Untitled](pic-gceasy/Untitled%2028.png)
    
    最后可以看下导致 G1 GC 的原因：
    
    ![Untitled](pic-gceasy/Untitled%2029.png)
    
7. g1gc_4g_100_4m.log
   
    首先可以看看 JVM 的内存划分：
    
    ![Untitled](pic-gceasy/Untitled%2030.png)
    
    其次可以看看在 G1 GC 的一些关键性能指标，如吞吐量，延迟等：
    
    ![Untitled](pic-gceasy/Untitled%2031.png)
    
    其次可以看看在 G1 GC 中每个阶段花费的时间：
    
    ![Untitled](pic-gceasy/Untitled%2032.png)
    
    然后可以看看 G1 GC 的过程中，STW 和并发执行的时间消耗：
    
    ![Untitled](pic-gceasy/Untitled%2033.png)
    
    最后可以看下导致 G1 GC 的原因：
    
    ![Untitled](pic-gceasy/Untitled%2034.png)
    
8. g1gc_4g_200_4m.log
   
    首先可以看看 JVM 的内存划分：
    
    ![Untitled](pic-gceasy/Untitled%2035.png)
    
    其次可以看看在 G1 GC 的一些关键性能指标，如吞吐量，延迟等：
    
    ![Untitled](pic-gceasy/Untitled%2036.png)
    
    其次可以看看在 G1 GC 中每个阶段花费的时间：
    
    ![Untitled](pic-gceasy/Untitled%2037.png)
    
    然后可以看看 G1 GC 的过程中，STW 和并发执行的时间消耗：
    
    ![Untitled](pic-gceasy/Untitled%2038.png)
    
    最后可以看下导致 G1 GC 的原因：
    
    ![Untitled](pic-gceasy/Untitled%2039.png)
    
9. g1gc_4g_500_4m.log
   
    首先可以看看 JVM 的内存划分：
    
    ![Untitled](pic-gceasy/Untitled%2040.png)
    
    其次可以看看在 G1 GC 的一些关键性能指标，如吞吐量，延迟等：
    
    ![Untitled](pic-gceasy/Untitled%2041.png)
    
    其次可以看看在 G1 GC 中每个阶段花费的时间：
    
    ![Untitled](pic-gceasy/Untitled%2042.png)
    
    然后可以看看 G1 GC 的过程中，STW 和并发执行的时间消耗：
    
    ![Untitled](pic-gceasy/Untitled%2043.png)
    
    最后可以看下导致 G1 GC 的原因：
    
    ![Untitled](pic-gceasy/Untitled%2044.png)
    
10. g1gc_8g_100_1m.log
    
    首先可以看看 JVM 的内存划分：
    
    ![Untitled](pic-gceasy/Untitled%2045.png)
    
    其次可以看看在 G1 GC 的一些关键性能指标，如吞吐量，延迟等：
    
    ![Untitled](pic-gceasy/Untitled%2046.png)
    
    其次可以看看在 G1 GC 中每个阶段花费的时间：
    
    ![Untitled](pic-gceasy/Untitled%2047.png)
    
    然后可以看看 G1 GC 的过程中，STW 和并发执行的时间消耗：
    
    ![Untitled](pic-gceasy/Untitled%2048.png)
    
    最后可以看下导致 G1 GC 的原因：
    
    ![Untitled](pic-gceasy/Untitled%2049.png)
    
11. g1gc_8g_200_1m.log
    
    首先可以看看 JVM 的内存划分：
    
    ![Untitled](pic-gceasy/Untitled%2050.png)
    
    其次可以看看在 G1 GC 的一些关键性能指标，如吞吐量，延迟等：
    
    ![Untitled](pic-gceasy/Untitled%2051.png)
    
    其次可以看看在 G1 GC 中每个阶段花费的时间：
    
    ![Untitled](pic-gceasy/Untitled%2052.png)
    
    然后可以看看 G1 GC 的过程中，STW 和并发执行的时间消耗：
    
    ![Untitled](pic-gceasy/Untitled%2053.png)
    
    最后可以看下导致 G1 GC 的原因：
    
    ![Untitled](pic-gceasy/Untitled%2054.png)
    
12. g1gc_8g_500_1m.log
    
    首先可以看看 JVM 的内存划分：
    
    ![Untitled](pic-gceasy/Untitled%2055.png)
    
    其次可以看看在 G1 GC 的一些关键性能指标，如吞吐量，延迟等：
    
    ![Untitled](pic-gceasy/Untitled%2056.png)
    
    其次可以看看在 G1 GC 中每个阶段花费的时间：
    
    ![Untitled](pic-gceasy/Untitled%2057.png)
    
    然后可以看看 G1 GC 的过程中，STW 和并发执行的时间消耗：
    
    ![Untitled](pic-gceasy/Untitled%2058.png)
    
    最后可以看下导致 G1 GC 的原因：
    
    ![Untitled](pic-gceasy/Untitled%2059.png)
    
13. g1gc_8g_100_2m.log
    
    首先可以看看 JVM 的内存划分：
    
    ![Untitled](pic-gceasy/Untitled%2060.png)
    
    其次可以看看在 G1 GC 的一些关键性能指标，如吞吐量，延迟等：
    
    ![Untitled](pic-gceasy/Untitled%2061.png)
    
    其次可以看看在 G1 GC 中每个阶段花费的时间：
    
    ![Untitled](pic-gceasy/Untitled%2062.png)
    
    然后可以看看 G1 GC 的过程中，STW 和并发执行的时间消耗：
    
    ![Untitled](pic-gceasy/Untitled%2063.png)
    
    最后可以看下导致 G1 GC 的原因：
    
    ![Untitled](pic-gceasy/Untitled%2064.png)
    
14. g1gc_8g_200_2m.log
    
    首先可以看看 JVM 的内存划分：
    
    ![Untitled](pic-gceasy/Untitled%2065.png)
    
    其次可以看看在 G1 GC 的一些关键性能指标，如吞吐量，延迟等：
    
    ![Untitled](pic-gceasy/Untitled%2066.png)
    
    其次可以看看在 G1 GC 中每个阶段花费的时间：
    
    ![Untitled](pic-gceasy/Untitled%2067.png)
    
    然后可以看看 G1 GC 的过程中，STW 和并发执行的时间消耗：
    
    ![Untitled](pic-gceasy/Untitled%2068.png)
    
    最后可以看下导致 G1 GC 的原因：
    
    ![Untitled](pic-gceasy/Untitled%2069.png)
    
15. g1gc_8g_500_2m.log
    
    首先可以看看 JVM 的内存划分：
    
    ![Untitled](pic-gceasy/Untitled%2070.png)
    
    其次可以看看在 G1 GC 的一些关键性能指标，如吞吐量，延迟等：
    
    ![Untitled](pic-gceasy/Untitled%2071.png)
    
    其次可以看看在 G1 GC 中每个阶段花费的时间：
    
    ![Untitled](pic-gceasy/Untitled%2072.png)
    
    然后可以看看 G1 GC 的过程中，STW 和并发执行的时间消耗：
    
    ![Untitled](pic-gceasy/Untitled%2073.png)
    
    最后可以看下导致 G1 GC 的原因：
    
    ![Untitled](pic-gceasy/Untitled%2074.png)
    
16. g1gc_8g_100_4m.log
    
    首先可以看看 JVM 的内存划分：
    
    ![Untitled](pic-gceasy/Untitled%2075.png)
    
    其次可以看看在 G1 GC 的一些关键性能指标，如吞吐量，延迟等：
    
    ![Untitled](pic-gceasy/Untitled%2076.png)
    
    其次可以看看在 G1 GC 中每个阶段花费的时间：
    
    ![Untitled](pic-gceasy/Untitled%2077.png)
    
    然后可以看看 G1 GC 的过程中，STW 和并发执行的时间消耗：
    
    ![Untitled](pic-gceasy/Untitled%2078.png)
    
    最后可以看下导致 G1 GC 的原因：
    
    ![Untitled](pic-gceasy/Untitled%2079.png)
    
17. g1gc_8g_200_4m.log
    
    首先可以看看 JVM 的内存划分：
    
    ![Untitled](pic-gceasy/Untitled%2080.png)
    
    其次可以看看在 G1 GC 的一些关键性能指标，如吞吐量，延迟等：
    
    ![Untitled](pic-gceasy/Untitled%2081.png)
    
    其次可以看看在 G1 GC 中每个阶段花费的时间：
    
    ![Untitled](pic-gceasy/Untitled%2082.png)
    
    然后可以看看 G1 GC 的过程中，STW 和并发执行的时间消耗：
    
    ![Untitled](pic-gceasy/Untitled%2083.png)
    
    最后可以看下导致 G1 GC 的原因：
    
    ![Untitled](pic-gceasy/Untitled%2084.png)
    
18. g1gc_8g_500_4m.log
    
    首先可以看看 JVM 的内存划分：
    
    ![Untitled](pic-gceasy/Untitled%2085.png)
    
    其次可以看看在 G1 GC 的一些关键性能指标，如吞吐量，延迟等：
    
    ![Untitled](pic-gceasy/Untitled%2086.png)
    
    其次可以看看在 G1 GC 中每个阶段花费的时间：
    
    ![Untitled](pic-gceasy/Untitled%2087.png)
    
    然后可以看看 G1 GC 的过程中，STW 和并发执行的时间消耗：
    
    ![Untitled](pic-gceasy/Untitled%2088.png)
    
    最后可以看下导致 G1 GC 的原因：
    
    ![Untitled](pic-gceasy/Untitled%2089.png)
    
19. g1gc_16g_100_1m.log
    
    首先可以看看 JVM 的内存划分：
    
    ![Untitled](pic-gceasy/Untitled%2090.png)
    
    其次可以看看在 G1 GC 的一些关键性能指标，如吞吐量，延迟等：
    
    ![Untitled](pic-gceasy/Untitled%2091.png)
    
    其次可以看看在 G1 GC 中每个阶段花费的时间：
    
    ![Untitled](pic-gceasy/Untitled%2092.png)
    
    然后可以看看 G1 GC 的过程中，STW 和并发执行的时间消耗：
    
    ![Untitled](pic-gceasy/Untitled%2093.png)
    
    最后可以看下导致 G1 GC 的原因：
    
    ![Untitled](pic-gceasy/Untitled%2094.png)
    
20. g1gc_16g_200_1m.log
    
    首先可以看看 JVM 的内存划分：
    
    ![Untitled](pic-gceasy/Untitled%2095.png)
    
    其次可以看看在 G1 GC 的一些关键性能指标，如吞吐量，延迟等：
    
    ![Untitled](pic-gceasy/Untitled%2096.png)
    
    其次可以看看在 G1 GC 中每个阶段花费的时间：
    
    ![Untitled](pic-gceasy/Untitled%2097.png)
    
    然后可以看看 G1 GC 的过程中，STW 和并发执行的时间消耗：
    
    ![Untitled](pic-gceasy/Untitled%2098.png)
    
    最后可以看下导致 G1 GC 的原因：
    
    ![Untitled](pic-gceasy/Untitled%2099.png)
    
21. g1gc_16g_500_1m.log
    
    首先可以看看 JVM 的内存划分：
    
    ![Untitled](pic-gceasy/Untitled%20100.png)
    
    其次可以看看在 G1 GC 的一些关键性能指标，如吞吐量，延迟等：
    
    ![Untitled](pic-gceasy/Untitled%20101.png)
    
    其次可以看看在 G1 GC 中每个阶段花费的时间：
    
    ![Untitled](pic-gceasy/Untitled%20102.png)
    
    然后可以看看 G1 GC 的过程中，STW 和并发执行的时间消耗：
    
    ![Untitled](pic-gceasy/Untitled%20103.png)
    
    最后可以看下导致 G1 GC 的原因：
    
    ![Untitled](pic-gceasy/Untitled%20104.png)
    
22. g1gc_16g_100_2m.log
    
    首先可以看看 JVM 的内存划分：
    
    ![Untitled](pic-gceasy/Untitled%20105.png)
    
    其次可以看看在 G1 GC 的一些关键性能指标，如吞吐量，延迟等：
    
    ![Untitled](pic-gceasy/Untitled%20106.png)
    
    其次可以看看在 G1 GC 中每个阶段花费的时间：
    
    ![Untitled](pic-gceasy/Untitled%20107.png)
    
    然后可以看看 G1 GC 的过程中，STW 和并发执行的时间消耗：
    
    ![Untitled](pic-gceasy/Untitled%20108.png)
    
    最后可以看下导致 G1 GC 的原因：
    
    ![Untitled](pic-gceasy/Untitled%20109.png)
    
23. g1gc_16g_200_2m.log
    
    首先可以看看 JVM 的内存划分：
    
    ![Untitled](pic-gceasy/Untitled%20110.png)
    
    其次可以看看在 G1 GC 的一些关键性能指标，如吞吐量，延迟等：
    
    ![Untitled](pic-gceasy/Untitled%20111.png)
    
    其次可以看看在 G1 GC 中每个阶段花费的时间：
    
    ![Untitled](pic-gceasy/Untitled%20112.png)
    
    然后可以看看 G1 GC 的过程中，STW 和并发执行的时间消耗：
    
    ![Untitled](pic-gceasy/Untitled%20113.png)
    
    最后可以看下导致 G1 GC 的原因：
    
    ![Untitled](pic-gceasy/Untitled%20114.png)
    
24. g1gc_16g_500_2m.log
    
    首先可以看看 JVM 的内存划分：
    
    ![Untitled](pic-gceasy/Untitled%20115.png)
    
    其次可以看看在 G1 GC 的一些关键性能指标，如吞吐量，延迟等：
    
    ![Untitled](pic-gceasy/Untitled%20116.png)
    
    其次可以看看在 G1 GC 中每个阶段花费的时间：
    
    ![Untitled](pic-gceasy/Untitled%20117.png)
    
    然后可以看看 G1 GC 的过程中，STW 和并发执行的时间消耗：
    
    ![Untitled](pic-gceasy/Untitled%20118.png)
    
    最后可以看下导致 G1 GC 的原因：
    
    ![Untitled](pic-gceasy/Untitled%20119.png)
    
25. g1gc_16g_100_4m.log
    
    首先可以看看 JVM 的内存划分：
    
    ![Untitled](pic-gceasy/Untitled%20120.png)
    
    其次可以看看在 G1 GC 的一些关键性能指标，如吞吐量，延迟等：
    
    ![Untitled](pic-gceasy/Untitled%20121.png)
    
    其次可以看看在 G1 GC 中每个阶段花费的时间：
    
    ![Untitled](pic-gceasy/Untitled%20122.png)
    
    然后可以看看 G1 GC 的过程中，STW 和并发执行的时间消耗：
    
    ![Untitled](pic-gceasy/Untitled%20123.png)
    
    最后可以看下导致 G1 GC 的原因：
    
    ![Untitled](pic-gceasy/Untitled%20124.png)
    
26. g1gc_16g_200_4m.log
    
    首先可以看看 JVM 的内存划分：
    
    ![Untitled](pic-gceasy/Untitled%20125.png)
    
    其次可以看看在 G1 GC 的一些关键性能指标，如吞吐量，延迟等：
    
    ![Untitled](pic-gceasy/Untitled%20126.png)
    
    其次可以看看在 G1 GC 中每个阶段花费的时间：
    
    ![Untitled](pic-gceasy/Untitled%20127.png)
    
    然后可以看看 G1 GC 的过程中，STW 和并发执行的时间消耗：
    
    ![Untitled](pic-gceasy/Untitled%20128.png)
    
    最后可以看下导致 G1 GC 的原因：
    
    ![Untitled](pic-gceasy/Untitled%20129.png)
    
27. g1gc_16g_500_4m.log
    
    首先可以看看 JVM 的内存划分：
    
    ![Untitled](pic-gceasy/Untitled%20130.png)
    
    其次可以看看在 G1 GC 的一些关键性能指标，如吞吐量，延迟等：
    
    ![Untitled](pic-gceasy/Untitled%20131.png)
    
    其次可以看看在 G1 GC 中每个阶段花费的时间：
    
    ![Untitled](pic-gceasy/Untitled%20132.png)
    
    然后可以看看 G1 GC 的过程中，STW 和并发执行的时间消耗：
    
    ![Untitled](pic-gceasy/Untitled%20133.png)
    
    最后可以看下导致 G1 GC 的原因：
    
    ![Untitled](pic-gceasy/Untitled%20134.png)
    

接下来来看 ZGC 垃圾收集的结果：

1. zgc_4g_100.log
   
    首先可以看看 JVM 的内存划分：
    
    ![Untitled](pic-gceasy/Untitled%20135.png)
    
    其次可以看看在 ZGC 的一些关键性能指标，如吞吐量，延迟等：
    
    ![Untitled](pic-gceasy/Untitled%20136.png)
    
    然后可以看看 ZGC 中每个阶段花费的时间：
    
    ![Untitled](pic-gceasy/Untitled%20137.png)
    
    然后可以看看 ZGC 的过程中，STW 和并发执行的时间消耗：
    
    ![Untitled](pic-gceasy/Untitled%20138.png)
    
    最后可以看下导致 ZGC 的原因：
    
    ![Untitled](pic-gceasy/Untitled%20139.png)
    
2. zgc_4g_200.log
   
    首先可以看看 JVM 的内存划分：
    
    ![Untitled](pic-gceasy/Untitled%20140.png)
    
    其次可以看看在 ZGC 的一些关键性能指标，如吞吐量，延迟等：
    
    ![Untitled](pic-gceasy/Untitled%20141.png)
    
    然后可以看看 ZGC 中每个阶段花费的时间：
    
    ![Untitled](pic-gceasy/Untitled%20142.png)
    
    然后可以看看 ZGC 的过程中，STW 和并发执行的时间消耗：
    
    ![Untitled](pic-gceasy/Untitled%20143.png)
    
    最后可以看下导致 ZGC 的原因：
    
    ![Untitled](pic-gceasy/Untitled%20144.png)
    
3. zgc_4g_500.log
   
    首先可以看看 JVM 的内存划分：
    
    ![Untitled](pic-gceasy/Untitled%20145.png)
    
    其次可以看看在 ZGC 的一些关键性能指标，如吞吐量，延迟等：
    
    ![Untitled](pic-gceasy/Untitled%20146.png)
    
    然后可以看看 ZGC 中每个阶段花费的时间：
    
    ![Untitled](pic-gceasy/Untitled%20147.png)
    
    然后可以看看 ZGC 的过程中，STW 和并发执行的时间消耗：
    
    ![Untitled](pic-gceasy/Untitled%20148.png)
    
    最后可以看下导致 ZGC 的原因：
    
    ![Untitled](pic-gceasy/Untitled%20149.png)
    
4. zgc_8g_100.log
   
    首先可以看看 JVM 的内存划分：
    
    ![Untitled](pic-gceasy/Untitled%20150.png)
    
    其次可以看看在 ZGC 的一些关键性能指标，如吞吐量，延迟等：
    
    ![Untitled](pic-gceasy/Untitled%20151.png)
    
    然后可以看看 ZGC 中每个阶段花费的时间：
    
    ![Untitled](pic-gceasy/Untitled%20152.png)
    
    然后可以看看 ZGC 的过程中，STW 和并发执行的时间消耗：
    
    ![Untitled](pic-gceasy/Untitled%20153.png)
    
    最后可以看下导致 ZGC 的原因：
    
    ![Untitled](pic-gceasy/Untitled%20154.png)
    
5. zgc_8g_200.log
   
    首先可以看看 JVM 的内存划分：
    
    ![Untitled](pic-gceasy/Untitled%20155.png)
    
    其次可以看看在 ZGC 的一些关键性能指标，如吞吐量，延迟等：
    
    ![Untitled](pic-gceasy/Untitled%20156.png)
    
    然后可以看看 ZGC 中每个阶段花费的时间：
    
    ![Untitled](pic-gceasy/Untitled%20157.png)
    
    然后可以看看 ZGC 的过程中，STW 和并发执行的时间消耗：
    
    ![Untitled](pic-gceasy/Untitled%20158.png)
    
    最后可以看下导致 ZGC 的原因：
    
    ![Untitled](pic-gceasy/Untitled%20159.png)
    
6. zgc_8g_500.log
   
    首先可以看看 JVM 的内存划分：
    
    ![Untitled](pic-gceasy/Untitled%20160.png)
    
    其次可以看看在 ZGC 的一些关键性能指标，如吞吐量，延迟等：
    
    ![Untitled](pic-gceasy/Untitled%20161.png)
    
    然后可以看看 ZGC 中每个阶段花费的时间：
    
    ![Untitled](pic-gceasy/Untitled%20162.png)
    
    然后可以看看 ZGC 的过程中，STW 和并发执行的时间消耗：
    
    ![Untitled](pic-gceasy/Untitled%20163.png)
    
    最后可以看下导致 ZGC 的原因：
    
    ![Untitled](pic-gceasy/Untitled%20164.png)
    
7. zgc_16g_100.log
   
    首先可以看看 JVM 的内存划分：
    
    ![Untitled](pic-gceasy/Untitled%20165.png)
    
    其次可以看看在 ZGC 的一些关键性能指标，如吞吐量，延迟等：
    
    ![Untitled](pic-gceasy/Untitled%20166.png)
    
    然后可以看看 ZGC 中每个阶段花费的时间：
    
    ![Untitled](pic-gceasy/Untitled%20167.png)
    
    然后可以看看 ZGC 的过程中，STW 和并发执行的时间消耗：
    
    ![Untitled](pic-gceasy/Untitled%20168.png)
    
    最后可以看下导致 ZGC 的原因：
    
    ![Untitled](pic-gceasy/Untitled%20169.png)
    
8. zgc_16g_200.log
   
    首先可以看看 JVM 的内存划分：
    
    ![Untitled](pic-gceasy/Untitled%20170.png)
    
    其次可以看看在 ZGC 的一些关键性能指标，如吞吐量，延迟等：
    
    ![Untitled](pic-gceasy/Untitled%20171.png)
    
    然后可以看看 ZGC 中每个阶段花费的时间：
    
    ![Untitled](pic-gceasy/Untitled%20172.png)
    
    然后可以看看 ZGC 的过程中，STW 和并发执行的时间消耗：
    
    ![Untitled](pic-gceasy/Untitled%20173.png)
    
    最后可以看下导致 ZGC 的原因：
    
    ![Untitled](pic-gceasy/Untitled%20174.png)
    
9. zgc_16g_500.log
   
    首先可以看看 JVM 的内存划分：
    
    ![Untitled](pic-gceasy/Untitled%20175.png)
    
    其次可以看看在 ZGC 的一些关键性能指标，如吞吐量，延迟等：
    
    ![Untitled](pic-gceasy/Untitled%20176.png)
    
    然后可以看看 ZGC 中每个阶段花费的时间：
    
    ![Untitled](pic-gceasy/Untitled%20177.png)
    
    然后可以看看 ZGC 的过程中，STW 和并发执行的时间消耗：
    
    ![Untitled](pic-gceasy/Untitled%20178.png)
    
    最后可以看下导致 ZGC 的原因：
    
    ![Untitled](pic-gceasy/Untitled%20179.png)