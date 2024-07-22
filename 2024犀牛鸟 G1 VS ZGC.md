# 2024犀牛鸟 G1 VS ZGC

- [2024犀牛鸟 G1 VS ZGC](#2024犀牛鸟-g1-vs-zgc)
- [GC 分析](#gc-分析)
  - [G1 介绍](#g1-介绍)
  - [ZGC 介绍](#zgc-介绍)
  - [G1 和 ZGC 分析](#g1-和-zgc-分析)
- [GC 实践](#gc-实践)
  - [参数设置](#参数设置)
  - [日志分析](#日志分析)
    - [G1 日志举例分析](#g1-日志举例分析)
    - [ZGC 日志举例分析](#zgc-日志举例分析)
    - [G1-4g-100ms-1m VS G1-4g-200ms-1m VS G1-4g-500ms-1m](#g1-4g-100ms-1m-vs-g1-4g-200ms-1m-vs-g1-4g-500ms-1m)
    - [G1-4g-100ms-1m VS G1-4g-100ms-2m VS G1-4g-100ms-4m](#g1-4g-100ms-1m-vs-g1-4g-100ms-2m-vs-g1-4g-100ms-4m)
    - [G1-4g-100ms-1m VS G1-8g-100ms-1m VS G1-16g-100ms-1m](#g1-4g-100ms-1m-vs-g1-8g-100ms-1m-vs-g1-16g-100ms-1m)
    - [ZGC-4g-100ms-4g VS ZGC-4g-200ms-4g VS ZGC-4g-500ms-4g](#zgc-4g-100ms-4g-vs-zgc-4g-200ms-4g-vs-zgc-4g-500ms-4g)
    - [ZGC-4g-100ms-4g VS ZGC-8g-100ms-8g VS ZGC-16g-100ms-16g](#zgc-4g-100ms-4g-vs-zgc-8g-100ms-8g-vs-zgc-16g-100ms-16g)
    - [G1-4g-100-1m VS ZGC-4g-100](#g1-4g-100-1m-vs-zgc-4g-100)
    - [G1-16g-100-1m VS ZGC-16g-100](#g1-16g-100-1m-vs-zgc-16g-100)
  - [结果分析](#结果分析)
  - [附加思考](#附加思考)
    - [内存开销方面，G1 VS ZGC](#内存开销方面g1-vs-zgc)
    - [支持不同大小堆，G1 VS ZGC](#支持不同大小堆g1-vs-zgc)

# GC 分析

首先，我先按照自己的理解，对 G1 GC 和 ZGC 进行一个介绍，然后对他们在堆大小，停顿时间和吞吐量方面的差异进行分析。

## G1 介绍

对于 G1 来说，它不仅追求低停顿，并且希望可以建立一个可预测的停顿时间模型，让用户明确指定在某一个时间段内完成 GC。

我认为 G1  非常有创意的点在于引入了 “Region” 的概念，不再物理隔离新生代和老年代，将堆分成了大小相等的独立区域，这样的话，我们就可以对每个区域独立回收，这样使得可预测成为了可能，通过记录每个 Region 垃圾回收的时间以及回收所收获的空间，并维护一个优先列表，我们就可以做到每次回收的时候，根据允许的收集时间，优先回收价值最大的 Region 块。这样细化的划分，把内存化整为零，要比新生代老年代更加的精细化，也可以带来更多的收益。

G1 的分区采用了 Region 的思路，将整个堆空间分成了大小相等的内存区域，每次分配对象空间将逐段的使用内存。每个 Region 不指定为某个代服务，可以按需在新生代和老年代之间切换。在每个 Region 的内部，又分为了若干个 512 Byte 的 Card，Card 是标识堆内存的最小可用粒度，所有 Region 的 Card 都会被记录在全局卡片表（ Global Card Table）中。每次对内存的回收，实际都是对指定 Region 的 Card 进行处理。G1 对内存的使用以 Region 为单位，而对对象的分配以 Card 为单位。

对于 G1 的分区模型，存在以下几个概念：

1. 巨型对象（ Humongous Region ）
   
    巨型对象就是说这个对象的大小已经超过了 Region 的一半，这种对象的移动成本很高，而且有可能一个 Region 不能容纳一个巨型对象，所以不能之间在 TLAB 中进行分配，而是直接在老年代分配。
    
2. 已记忆集合（ Remember Set / RSet ）
   
    每个 Region 都有一个 RSet，用来记录该 Region 对象的引用对象所在的 Region。通过使用 RSet，在做可达性分析的时候就可以避免全堆扫描，RSet 类似一个反向指针，只需要扫描 RSet 就可以确定本分区内的对象是否存活，进而确定本分区的对象存活情况。
    
3. Per Region Table（PRT）
   
    RSet 内部使用 PRT 记录分区的引用情况。为了区分热点的 Region 和 非热点的 Region，PRT 分了三种模式记录引用（稀少、细粒度、粗粒度），依次占用更少的空间，记录更少的内容。
    

G1 也存在分代的思想，G1 将内存在逻辑上划分为年轻代和老年代，其中年轻代又划分为Eden空间和Survivor空间。但年轻代空间并不是固定不变的，当现有年轻代分区占满时，JVM会分配新的空闲分区加入到年轻代空间。而由于分区的思想，每个应用线程和 GC 线程都会使用独立的使用分区，不用顾及分区是否连续，进而可以减少同步时间，这种独立的分区就是本地分配缓冲区（Lab）。

其中，应用线程可以独占一个本地缓冲区（ TLAB ）来创建的对象，而大部分都会落入Eden区域（巨型对象或分配失败除外）；而每次垃圾收集时，每个GC线程同样可以独占一个本地缓冲区（GCLAB）用来转移对象，每次回收会将对象复制到Suvivor空间或老年代空间；对于从Eden/Survivor空间晋升（ Promotion ）到Survivor/老年代空间的对象，同样有GC独占的本地缓冲区进行操作，该部分称为晋升本地缓冲区（ PLAB ）。

收集集合（ CSet ）代表每次GC暂停时回收的一系列目标分区。在任意一次收集暂停中，CSet所有分区都会被释放，内部存活的对象都会被转移到分配的空闲分区中。CSet 可以分为年轻代收集集合和混合收集集合。无论是年轻代收集，还是混合收集，工作的机制都是一致的。年轻代收集CSet只容纳年轻代分区，而混合收集会通过启发式算法，在老年代候选回收分区中，筛选出回收收益最高的分区添加到CSet中。

STAB（起始快照算法），它主要针对标记-清除垃圾收集器的并发标记阶段，非常适合G1的分区块的堆结构，同时解决了CMS的主要烦恼：重新标记暂停时间长带来的潜在风险。SATB会创建一个对象图，相当于堆的逻辑快照，从而确保并发标记阶段所有的垃圾对象都能通过快照被鉴别出来。当赋值语句发生时，应用将会改变了它的对象图，那么JVM需要记录被覆盖的对象。因此写前栅栏会在引用变更前，将值记录在SATB日志或缓冲区中。每个线程都会独占一个SATB缓冲区，初始有256条记录空间。当空间用尽时，线程会分配新的SATB缓冲区继续使用，而原有的缓冲去则加入全局列表中。最终在并发标记阶段，并发标记线程在标记的同时，还会定期检查和处理全局缓冲区列表的记录，然后根据标记位图分片的标记位，扫描引用字段来更新RSet。

对于 RSet 的维护，主要用到了写栅栏和并发优化线程。栅栏是指在原生代码段中，某些语句运行时，栅栏代码也会执行。G1 会在赋值语句中，使用写前栅栏和写后栅栏。而并发优化线程指的是只专注扫描日志缓冲区记录的卡片来维护更新RSet，并发优化线程永远是活跃的，一旦发现全局列表有记录存在，就开始并发处理。

对于 RSet 的维护，主要用到了写栅栏和并发优化线程。栅栏是指在原生代码段中，某些语句运行时，栅栏代码也会执行。G1 会在赋值语句中，使用写前栅栏和写后栅栏。而并发优化线程指的是只专注扫描日志缓冲区记录的卡片来维护更新RSet，并发优化线程永远是活跃的，一旦发现全局列表有记录存在，就开始并发处理。

G1 的垃圾收集过程主要是 4 个步骤：

初始标记 ➡️ 并发标记 ➡️ 最终标记 ➡️ 筛选回收。

其中最终标记主要是为了修正在并发标记期间因用户程序继续运作而导致标记产生变动的那一部分标记记录，虚拟机将这段时间对象变化记录在线程的 RSet Logs 里面，最终标记阶段需要把 RSet Logs 的数据合并到 RSet 中。这阶段需要停顿线程，但是可并行执行。

筛选回收的过程主要是首先对各个 Region 中的回收价值和成本进行排序，根据用户所期望的 GC 停顿时间来制定回收计划。此阶段其实也可以做到与用户程序一起并发执行，但是因为只回收一部分 Region，时间是用户可控制的，而且停顿用户线程将大幅度提高收集效率。

## ZGC 介绍

下面来说下另一种要探讨的GC —— ZGC。ZGC 是在 JDK 11 中推出的一款垃圾收集器，它的设计目标主要是适用了大内存低延迟服务的内存管理和回收。它可以实现：

- 停顿时间不超过10ms；
- 停顿时间不会随着堆的大小，或者活跃对象的大小而增加（对程序吞吐量影响小于15%）；
- 支持8MB~4TB级别的堆（未来支持16TB）。

ZGC 中已经不存在分代的概念了。ZGC 和 G1 等垃圾回收器一样，也会将堆划分成很多的 Region。ZGC 的 Region 有小、中、大三种类型：Small Region，容量固定为 2M， 存放小于 256K 的对象；Medium Region，容量固定为 32M，放置大于等于 256K，并小于 4M 的对象；Large Region：容量不固定，可以动态变化，但必须为 2M 的整数倍，用于放置大于等于 4MB 的大对象。

对于 ZGC 来说，主要有两个关键技术，着色指针和读屏障。

- 着色指针是一种将信息存储在指针中的技术。ZGC仅支持64位系统，它把64位虚拟地址空间划分为多个子空间。ZGC 的一大创举是将 GC 信息保存在了染色指针上。在 64 位 JVM 中，对象指针是 64 位，在这个 64 位的指针上，高 18 位都是 0，暂时不用来寻址。剩下的 48 位支持的内存可以达到 64 TB（ $2^{46}$ ），这可以满足多数大型服务器的需要了。不过 ZGC 并没有把 46 位都用来保存对象信息，而是用高 4 位保存了四个标志位，这样 ZGC 可以管理的最大内存可以达到 4 TB（ $2^{42}$ ）。通过这四个标志位，JVM 可以从指针上直接看到对象的标记状态（Marked0、Marked1）、是否进入了重分配集（Remapped）、是否需要通过 finalize 方法来访问到（暂不使用）。无需进行对象访问就可以获得 GC 信息，这大大提高了 GC 效率。
  
    当应用程序创建对象时，首先在堆空间申请一个虚拟地址，但该虚拟地址并不会映射到真正的物理地址。ZGC同时会为该对象在M0、M1和Remapped地址空间分别申请一个虚拟地址，且这三个虚拟地址对应同一个物理地址，但这三个空间在同一时间有且只有一个空间有效。ZGC之所以设置三个虚拟地址空间，是因为它使用“空间换时间”思想，去降低GC停顿时间。“空间换时间”中的空间是虚拟空间，而不是真正的物理空间。ZGC实际仅使用64位地址空间的41位，而第42、45位存储元数据，第47\~63位固定为0。ZGC将对象存活信息存储在42~45位中，这与传统的垃圾回收并将对象存活信息放在对象头中完全不同。
    
- 读屏障指的是 JVM 向代码中插入一小段代码的技术，当应用线程从堆中读取对象引用的时候，就会执行这段代码。ZGC中读屏障的代码作用：在对象标记和转移过程中，用于确定对象的引用地址是否满足条件，并作出相应动作。

ZGC 的垃圾回收过程主要分为以下几个步骤：

![Untitled](pic/Untitled.png)

1. 初始标记
2. 并发标记：GC 线程和 Java 应用线程会并行运行。不过在 ZGC 中，有下面几点不同：
    - GC 标记线程访问对象时，如果对象地址视图是 Remapped，就把对象地址视图切换到 Marked0，如果对象地址视图已经是 Marked0，说明已经被其他标记线程访问过了，跳过不处理。
    - 标记过程中Java 应用线程新创建的对象会直接进入 Marked0 视图。
    - 标记过程中Java 应用线程访问对象时，如果对象的地址视图是 Remapped，使用读屏障把对象地址视图切换到 Marked0。
    - 标记结束后，如果对象地址视图是 Marked0，那就是活跃的，如果对象地址视图是 Remapped，那就是不活跃的。
3. 并发转移准备：判断哪些对象需要转移
4. 再标记
5. 初始转移：转移就是把活跃对象复制到新的内存，之前的内存空间可以被回收。
6. 并发转移：并发转移过程 GC 线程和 Java 线程是并发进行的。转移过程中对象视图会被切回 Remapped 。不过在 ZGC 中，有下面几点不同：
    - 如果 GC 线程访问对象的视图是 Marked0，则转移对象，并把对象视图设置成 Remapped。
    - 如果 GC 线程访问对象的视图是 Remapped，说明被其他 GC 线程处理过，跳过不再处理。
    - 并发转移过程中 Java 应用线程创建的新对象地址视图是 Remapped。
    - 如果 Java 应用线程访问的对象被标记为活跃并且对象视图是 Marked0，则转移对象，并把对象视图设置成 Remapped。
7. 重定位
   
    转移过程对象的地址发生了变化，在这个阶段，把所有指向对象旧地址的指针调整到对象的新地址上。这个过程是在第二次GC发起时完成的，即上次的重定位和当次并发标记一起执行，减少扫描内存的次数。在完成重定位后，ZGC会释放那些完全包含垃圾对象的内存页。这些内存页会被返回给操作系统，或者被重用来分配新的对象。
    

## G1 和 ZGC 分析

通过我上面的描述，分别对 G1 和 ZGC 进行了介绍，下面我将从堆大小，停顿时间和吞吐量方面的差异进行分析。

- 堆大小
    - 对于 G1 来说：
        - 适用范围：G1 垃圾收集器适用于中等到大规模的堆内存，通常在数百MB到数十GB之间。
        - 堆内存管理：G1 使用分区（Region）的方式管理堆内存，每个 Region 大小可以动态调整。通过这种方式，G1 可以在堆内存较大的情况下仍然保持较低的暂停时间。
        - 内存回收策略：G1 通过并行和并发的方式进行垃圾收集，分为年轻代和老年代垃圾收集。G1 会优先回收那些垃圾最多的 Region，以最大化内存回收效率。
    - 对于 ZGC 来：
        - 适用范围：ZGC 是专为超大规模堆内存设计的垃圾收集器，可以处理TB级别的堆内存。
        - 堆内存管理：ZGC 使用基于着色指针和读屏障的技术来管理堆内存，这使得它能够高效处理非常大的堆内存。
        - 内存回收策略：ZGC 使用并发标记和并发压缩的方式进行垃圾收集，尽量减少垃圾收集对应用程序的暂停时间。ZGC 的目标是将暂停时间控制在10毫秒以下，即使在非常大的堆内存下也能保持这一目标。
- 停顿时间
    - 对于 G1 来说，它使用了标记-复制算法，它的停顿时间主要产生在下面三个阶段：
        1. 标记阶段：初始标记阶段是 STW 的，但是由于 GC Roots 的数量不多，通常该阶段非常短；再标记阶段需要重新标记并发标记阶段发生变化的对象，该阶段是 STW 的。
        2. 清理阶段：清点出有存活对象的分区和没有存活对象的分区，该阶段不会清理垃圾对象，也不会执行存活对象的复制。该阶段是STW的。
        3. 复制阶段：转移阶段需要分配新内存和复制对象的成员变量。转移阶段是STW的，其中内存分配通常耗时非常短，但对象成员变量的复制耗时有可能较长，这是因为复制耗时与存活对象数量与对象复杂度成正比。对象越复杂，复制耗时越长。
        
        G1 停顿时间的瓶颈主要是标记-复制中的转移阶段 STW。由于 G1 并没有解决转移过程中准确定位对象地址的问题，所以导致转移阶段无法和标记阶段一样并发执行。
        
    - 对于 ZGC 来说，ZGC只有三个 STW 阶段：初始标记，再标记，初始转移。
        1. 初始标记、初始转移：只需要扫描所有 GC Roots，其处理时间和 GC Roots 的数量成正比，一般情况耗时非常短；
        2. 再标记：STW时间很短，最多1ms，超过1ms则再次进入并发标记阶段。
        
        即，ZGC几乎所有暂停都只依赖于 GC Roots 集合大小，停顿时间不会随着堆的大小或者活跃对象的大小而增加。与 ZGC 对比，G1 的转移阶段完全 STW 的，且停顿时间随存活对象的大小增加而增加。
    
- 吞吐量
  
    吞吐量指的是 CPU 用于运行用户代码的时间与 CPU 总消耗时间的比值，即吞吐量 = 运行用户代码时间 /（运行用户代码时间 + 垃圾收集时间）。
    
    - 对于 G1 来说：
        - 设计目标：G1 的设计目标之一是保持较高的吞吐量，同时尽量减少垃圾收集的暂停时间。
        - 工作机制：G1 通过分区堆内存和并行处理来提高吞吐量。它会在后台并发地进行垃圾收集操作，尽量减少对应用程序运行的影响。
        - 吞吐量表现：在中等到大规模堆内存下，G1 通常能提供较高的吞吐量。然而，在需要频繁进行垃圾收集的情况下，G1 的吞吐量可能会受到影响，因为每次垃圾收集都会占用一定的CPU资源。
    - 对于 ZGC 来说
        - 设计目标：ZGC 的主要设计目标是极低的暂停时间，而不是最高的吞吐量。
        - 工作机制：ZGC 使用并发标记、并发重定位和并发压缩的方式进行垃圾收集，尽量将垃圾收集操作分散到应用程序的运行过程中，减少暂停时间。
        - 吞吐量表现：尽管 ZGC 也能提供良好的吞吐量，但其主要优势在于能够在非常低的暂停时间下处理超大规模的堆内存。因此，ZGC 在某些情况下的吞吐量可能会低于 G1，但它能显著减少垃圾收集对应用程序的影响，从而提高应用的响应速度和整体性能。

# GC 实践

## 参数设置

下面为测试 java 程序，它会频繁的进行对象分配，进而产生 GC。

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GCTuningDemo {
    private static final int OBJECT_SIZE = 1024; // 1KB
    private static final int LIST_SIZE = 10000;  // 10K个对象 
    private static final int ITERATIONS = 1000;  // 迭代次数  

    public static void main(String[] args) {
        List<byte[]> list = new ArrayList<>();
        Random random = new Random();

        for (int i = 0; i < ITERATIONS; i++) {
            // 进度条显示
            printProgress("Iteration", i, ITERATIONS);

            for (int j = 0; j < LIST_SIZE; j++) {
                list.add(new byte[OBJECT_SIZE]);
            }
            // 模拟随机删除
            for (int j = 0; j < LIST_SIZE / 2; j++) {
                list.remove(random.nextInt(list.size()));
            }
        }
        System.out.println("\nCompleted all iterations.");
    }

    private static void printProgress(String task, int current, int total) {
        int progress = (int) ((current / (double) total) * 100);
        StringBuilder progressBar = new StringBuilder("[");
        int progressBarLength = 50; // 进度条长度
        int filledLength = (int) (progressBarLength * progress / 100.0);

        for (int i = 0; i < progressBarLength; i++) {
            if (i < filledLength) {
                progressBar.append("=");
            } else {
                progressBar.append(" ");
            }
        }
        progressBar.append("]");

        System.out.printf("\r%s: %s %d%%", task, progressBar.toString(), progress);
    }
}
```

我使用了本地虚拟机进行测试，因为内存需求比较大，我配置了 16 GB 内存和 4 核的 CPU。

![Untitled](pic/Untitled%201.png)

我在服务器安装了 TencentKonaJDK 17，而且可以看到，当前使用的是 G1 的垃圾收集器

![Untitled](pic/Untitled%202.png)

在调研后，我计划对于以下 4 个参数进行测试以得出实验结果。主要是因为这些参数对垃圾收集器的性能和行为有显著影响。

1. G1 GC 参数调整：
    - `XX:MaxGCPauseMillis`：根据应用需求调整最大GC停顿时间目标。通过调整这个参数，可以控制应用程序的停顿时间，适用于需要平衡吞吐量和响应时间的应用。
    - `XX:G1HeapRegionSize`：调整G1堆 Region 大小，以优化内存管理。通过调整这个参数，可以优化G1 GC的内存管理策略，适用于需要高吞吐量和较低停顿时间的应用。
2. ZGC 参数调整：
    - `XX:ZCollectionInterval`：根据应用需求调整ZGC的集合间隔。通过调整这个参数，可以控制垃圾收集的频率和停顿时间，适用于需要低停顿时间的应用。
    - `XX:SoftMaxHeapSize`：调整软最大堆内存大小，以控制内存使用。通过调整这个参数，可以控制堆内存的使用和垃圾收集频率，适用于需要管理大规模内存的应用。

我计划使用下面的数值对结果进行测试：

1. 堆大小：设置为4GB、8GB和16GB。
2. 停顿时间目标：设置为100ms、200ms和500ms。
3. GC类型：G1 GC和ZGC。

我们将使用以下参数进行G1 GC的实验：

```
java -Xms<size> -Xmx<size> -XX:+UseG1GC -XX:MaxGCPauseMillis=<pause> -XX:G1HeapRegionSize=<region_size> -Xlog:gc*:file=g1gc_<size>_<pause>_<region_size>.log:time,level,tags GCTuningDemo
```

- `<size>`：堆内存大小（4g、8g、16g）。
- `<pause>`：最大GC停顿时间目标（100、200、500）。
- `<region_size>`：G1堆区域大小（例如：1m、2m、4m）。

我们将使用以下参数进行ZGC的实验：

```
java -Xms<size> -Xmx<size> -XX:+UseZGC -XX:SoftMaxHeapSize=<size> -XX:ZCollectionInterval=<interval> -XX:+PrintGCDetails -XX:+PrintGCDateStamps -Xloggc:zgc_<size>_<interval>.log GCTuningDemo

```

- `<size>`：堆内存大小（4g、8g、16g）。
- `<interval>`：ZGC的集合间隔（0.1、0.2、0.5），对应停顿时间目标（100ms、200ms、500ms）。

我主要使用了 gceasy 对输出的结果进行了分析，相关日志我放在同目录仓库中。

[gceasy.io](https://gceasy.ycrash.cn/gc-dashboard.jsp)

具体实验结果可见附录。

## 日志分析

下面我将分别分析对于 G1 和 ZGC 的输出日志进行分析。

### G1 日志举例分析

我先举几个 G1 的实验结果，对它的日志进行分析，后续不再贴日志，而是直接分析 GCEasy 给出的实验结果。

```
[2024-07-05T20:37:17.166-0700][info][gc] Using G1
[2024-07-05T20:37:17.266-0700][info][gc,init] Version: 17.0.11+1-LTS (release)
[2024-07-05T20:37:17.266-0700][info][gc,init] CPUs: 4 total, 4 available
[2024-07-05T20:37:17.266-0700][info][gc,init] Memory: 15953M
[2024-07-05T20:37:17.266-0700][info][gc,init] Large Page Support: Disabled
[2024-07-05T20:37:17.266-0700][info][gc,init] NUMA Support: Disabled
[2024-07-05T20:37:17.266-0700][info][gc,init] Compressed Oops: Enabled (Zero based)
[2024-07-05T20:37:17.266-0700][info][gc,init] Heap Region Size: 1M
[2024-07-05T20:37:17.266-0700][info][gc,init] Heap Min Capacity: 16G
[2024-07-05T20:37:17.266-0700][info][gc,init] Heap Initial Capacity: 16G
[2024-07-05T20:37:17.266-0700][info][gc,init] Heap Max Capacity: 16G
[2024-07-05T20:37:17.266-0700][info][gc,init] Pre-touch: Disabled
[2024-07-05T20:37:17.266-0700][info][gc,init] Parallel Workers: 4
[2024-07-05T20:37:17.266-0700][info][gc,init] Concurrent Workers: 1
[2024-07-05T20:37:17.266-0700][info][gc,init] Concurrent Refinement Workers: 4
[2024-07-05T20:37:17.266-0700][info][gc,init] Periodic GC: Disabled
[2024-07-05T20:37:17.273-0700][info][gc,metaspace] CDS archive(s) mapped at: [0x00007f1e3f000000-0x00007f1e3fbbb000-0x00007f1e3fbbb000), size 12300288, SharedBaseAddress: 0x00007f1e3f000000, ArchiveRelocationMode: 1.
[2024-07-05T20:37:17.273-0700][info][gc,metaspace] Compressed class space mapped at: 0x00007f1e40000000-0x00007f1e80000000, reserved size: 1073741824
[2024-07-05T20:37:17.273-0700][info][gc,metaspace] Narrow klass base: 0x00007f1e3f000000, Narrow klass shift: 0, Narrow klass range: 0x100000000
[2024-07-05T20:37:21.912-0700][info][gc,start    ] GC(0) Pause Young (Normal) (G1 Evacuation Pause)
[2024-07-05T20:37:21.913-0700][info][gc,task     ] GC(0) Using 4 workers of 4 for evacuation
[2024-07-05T20:37:22.216-0700][info][gc,mmu      ] GC(0) MMU target violated: 101.0ms (100.0ms/101.0ms)
[2024-07-05T20:37:22.216-0700][info][gc,phases   ] GC(0)   Pre Evacuate Collection Set: 1.6ms
[2024-07-05T20:37:22.216-0700][info][gc,phases   ] GC(0)   Merge Heap Roots: 0.9ms
[2024-07-05T20:37:22.216-0700][info][gc,phases   ] GC(0)   Evacuate Collection Set: 299.6ms
[2024-07-05T20:37:22.216-0700][info][gc,phases   ] GC(0)   Post Evacuate Collection Set: 1.0ms
[2024-07-05T20:37:22.216-0700][info][gc,phases   ] GC(0)   Other: 1.3ms
[2024-07-05T20:37:22.216-0700][info][gc,heap     ] GC(0) Eden regions: 819->0(716)
[2024-07-05T20:37:22.216-0700][info][gc,heap     ] GC(0) Survivor regions: 0->103(103)
[2024-07-05T20:37:22.216-0700][info][gc,heap     ] GC(0) Old regions: 0->496
[2024-07-05T20:37:22.216-0700][info][gc,heap     ] GC(0) Archive regions: 2->2
[2024-07-05T20:37:22.216-0700][info][gc,heap     ] GC(0) Humongous regions: 7->7
[2024-07-05T20:37:22.216-0700][info][gc,metaspace] GC(0) Metaspace: 229K(448K)->229K(448K) NonClass: 223K(320K)->223K(320K) Class: 6K(128K)->6K(128K)
[2024-07-05T20:37:22.217-0700][info][gc          ] GC(0) Pause Young (Normal) (G1 Evacuation Pause) 826M->606M(16384M) 304.560ms
[2024-07-05T20:37:22.217-0700][info][gc,cpu      ] GC(0) User=0.57s Sys=0.60s Real=0.31s
[2024-07-05T20:37:41.231-0700][info][gc,start    ] GC(1) Pause Young (Normal) (G1 Evacuation Pause)
[2024-07-05T20:37:41.231-0700][info][gc,task     ] GC(1) Using 4 workers of 4 for evacuation
[2024-07-05T20:37:41.571-0700][info][gc,mmu      ] GC(1) MMU target violated: 101.0ms (100.0ms/101.0ms)
[2024-07-05T20:37:41.571-0700][info][gc,phases   ] GC(1)   Pre Evacuate Collection Set: 0.9ms
[2024-07-05T20:37:41.571-0700][info][gc,phases   ] GC(1)   Merge Heap Roots: 0.7ms
[2024-07-05T20:37:41.571-0700][info][gc,phases   ] GC(1)   Evacuate Collection Set: 336.9ms
[2024-07-05T20:37:41.571-0700][info][gc,phases   ] GC(1)   Post Evacuate Collection Set: 1.0ms
[2024-07-05T20:37:41.571-0700][info][gc,phases   ] GC(1)   Other: 0.2ms
[2024-07-05T20:37:41.571-0700][info][gc,heap     ] GC(1) Eden regions: 716->0(716)
[2024-07-05T20:37:41.571-0700][info][gc,heap     ] GC(1) Survivor regions: 103->103(103)
[2024-07-05T20:37:41.571-0700][info][gc,heap     ] GC(1) Old regions: 496->1111
[2024-07-05T20:37:41.571-0700][info][gc,heap     ] GC(1) Archive regions: 2->2
[2024-07-05T20:37:41.571-0700][info][gc,heap     ] GC(1) Humongous regions: 11->11
[2024-07-05T20:37:41.571-0700][info][gc,metaspace] GC(1) Metaspace: 265K(448K)->265K(448K) NonClass: 258K(320K)->258K(320K) Class: 6K(128K)->6K(128K)
[2024-07-05T20:37:41.571-0700][info][gc          ] GC(1) Pause Young (Normal) (G1 Evacuation Pause) 1326M->1225M(16384M) 339.864ms
[2024-07-05T20:37:41.571-0700][info][gc,cpu      ] GC(1) User=0.68s Sys=0.62s Real=0.34s
```

这是对于 G1 ，将内存设置为 16G，停顿时间设置为 100ms，region 大小设置为 1M的实验结果。

- gc,init 阶段，这个阶段说明了一些关于本次 GC 的基本设置信息，主要包括：使用的是 G1 垃圾收集器、JVM 版本是 17.0.11、总计 4 个 CPU，且全部可用、系统内存为 15953MB、大页面支持和 NUMA 支持均被禁用、启用了压缩指针（Compressed Oops），零基址、region 区大小为 1MB、堆的最小、初始和最大容量均为 16GB、Pre-touch 功能被禁用、并行工作线程为 4 个，并发工作线程为 1 个，并发精细化工作线程为 4 个、周期性垃圾回收被禁用。
- gc,metaspace 阶段，这个阶段说明了关于元空间的一些参数设置。
- GC(0) 阶段，这个阶段是 GC 的第一个事件，它是一次新生代的垃圾回收，GC(0) 事件开始时的暂停、使用了 4 个工作线程来进行清除工作、MMU花费时间：101.0ms（目标为 100.0ms）、不同阶段的时间：集合预清理 1.6ms，合并堆根 0.9ms，清除集合 299.6ms，集合后清理 1.0ms，其他 1.3ms、堆内存变化：Eden 区从 819 减少到 0（716 新分配），Survivor 区从 0 增加到 103（共 103），老年代区从 0 增加到 496、元空间保持不变、整个暂停时间为 304.560ms，其中用户时间 0.57s，系统时间 0.60s，真实时间 0.31s。
- GC(1) 阶段，这个阶段也是一次对新生代的垃圾回收，可以关注下，Eden 区从 716 减少到 0（716 新分配），Survivor 区保持不变（103），老年代区从 496 增加到 1111。

对于我的代码，每次迭代创建 10,000 个对象，每个对象 1KB。每次迭代分配 10,000 KB = 10 MB 内存。删除 5,000 个对象，释放 5 MB 内存。每次迭代净分配 5 MB 内存。而我们可以看到，新生代的大小是 819MB，根据日志，我们可以看到 Eden 区的大小为 716 MB，经过 716MB/ 5MB =  144 次迭代后，Eden 区会被填满，于是此时会进行一次 Young GC，**整体计算下，我们可以计算出会进行 6~7 次GC，但实际进行了 14 次GC。**

![Untitled](pic/Untitled%203.png)

```
[2024-07-05T22:19:25.652-0700][info][gc,start    ] GC(12) Pause Young (Concurrent Start) (G1 Evacuation Pause)
[2024-07-05T22:19:25.652-0700][info][gc,task     ] GC(12) Using 4 workers of 4 for evacuation
[2024-07-05T22:19:28.453-0700][info][gc,mmu      ] GC(12) MMU target violated: 101.0ms (100.0ms/101.0ms)
[2024-07-05T22:19:28.453-0700][info][gc,phases   ] GC(12)   Pre Evacuate Collection Set: 1.0ms
[2024-07-05T22:19:28.453-0700][info][gc,phases   ] GC(12)   Merge Heap Roots: 2.1ms
[2024-07-05T22:19:28.453-0700][info][gc,phases   ] GC(12)   Evacuate Collection Set: 2792.0ms
[2024-07-05T22:19:28.453-0700][info][gc,phases   ] GC(12)   Post Evacuate Collection Set: 1.0ms
[2024-07-05T22:19:28.453-0700][info][gc,phases   ] GC(12)   Other: 5.1ms
[2024-07-05T22:19:28.453-0700][info][gc,heap     ] GC(12) Eden regions: 716->0(716)
[2024-07-05T22:19:28.453-0700][info][gc,heap     ] GC(12) Survivor regions: 103->103(103)
[2024-07-05T22:19:28.453-0700][info][gc,heap     ] GC(12) Old regions: 7748->8426
[2024-07-05T22:19:28.453-0700][info][gc,heap     ] GC(12) Archive regions: 2->2
[2024-07-05T22:19:28.453-0700][info][gc,heap     ] GC(12) Humongous regions: 74->74
[2024-07-05T22:19:28.453-0700][info][gc,metaspace] GC(12) Metaspace: 301K(512K)->301K(512K) NonClass: 294K(384K)->294K(384K) Class: 7K(128K)->7K(128K)
[2024-07-05T22:19:28.453-0700][info][gc          ] GC(12) Pause Young (Concurrent Start) (G1 Evacuation Pause) 8641M->8603M(16384M) 2801.189ms
[2024-07-05T22:19:28.453-0700][info][gc,cpu      ] GC(12) User=0.87s Sys=10.30s Real=2.80s
[2024-07-05T22:19:28.453-0700][info][gc          ] GC(13) Concurrent Mark Cycle
[2024-07-05T22:19:28.453-0700][info][gc,marking  ] GC(13) Concurrent Clear Claimed Marks
[2024-07-05T22:19:28.453-0700][info][gc,marking  ] GC(13) Concurrent Clear Claimed Marks 0.007ms
[2024-07-05T22:19:28.453-0700][info][gc,marking  ] GC(13) Concurrent Scan Root Regions
[2024-07-05T22:19:28.488-0700][info][gc,marking  ] GC(13) Concurrent Scan Root Regions 34.974ms
[2024-07-05T22:19:28.488-0700][info][gc,marking  ] GC(13) Concurrent Mark
[2024-07-05T22:19:28.488-0700][info][gc,marking  ] GC(13) Concurrent Mark From Roots
[2024-07-05T22:19:28.488-0700][info][gc,task     ] GC(13) Using 1 workers of 1 for marking
[2024-07-05T22:19:32.194-0700][info][gc,marking  ] GC(13) Concurrent Mark From Roots 3706.054ms
[2024-07-05T22:19:32.194-0700][info][gc,marking  ] GC(13) Concurrent Preclean
[2024-07-05T22:19:32.194-0700][info][gc,marking  ] GC(13) Concurrent Preclean 0.094ms
[2024-07-05T22:19:36.029-0700][info][gc,start    ] GC(13) Pause Remark
[2024-07-05T22:19:36.032-0700][info][gc          ] GC(13) Pause Remark 8608M->8501M(16384M) 2.945ms
[2024-07-05T22:19:36.032-0700][info][gc,cpu      ] GC(13) User=0.00s Sys=0.00s Real=0.00s
[2024-07-05T22:19:36.033-0700][info][gc,marking  ] GC(13) Concurrent Mark 7545.104ms
[2024-07-05T22:19:36.033-0700][info][gc,marking  ] GC(13) Concurrent Rebuild Remembered Sets
[2024-07-05T22:19:36.548-0700][info][gc,marking  ] GC(13) Concurrent Rebuild Remembered Sets 514.817ms
[2024-07-05T22:19:37.513-0700][info][gc,start    ] GC(13) Pause Cleanup
[2024-07-05T22:19:37.521-0700][info][gc          ] GC(13) Pause Cleanup 8501M->8501M(16384M) 7.901ms
[2024-07-05T22:19:37.521-0700][info][gc,cpu      ] GC(13) User=0.00s Sys=0.00s Real=0.01s
[2024-07-05T22:19:37.525-0700][info][gc,marking  ] GC(13) Concurrent Cleanup for Next Mark
[2024-07-05T22:19:40.021-0700][info][gc,marking  ] GC(13) Concurrent Cleanup for Next Mark 2496.795ms
[2024-07-05T22:19:40.021-0700][info][gc          ] GC(13) Concurrent Mark Cycle 11568.390ms
[2024-07-05T22:33:44.584-0700][info][gc,heap,exit] Heap
[2024-07-05T22:33:44.584-0700][info][gc,heap,exit]  garbage-first heap   total 16777216K, used 9256560K [0x0000000400000000, 0x0000000800000000)
[2024-07-05T22:33:44.584-0700][info][gc,heap,exit]   region size 1024K, 646 young (661504K), 103 survivors (105472K)
[2024-07-05T22:33:44.584-0700][info][gc,heap,exit]  Metaspace       used 302K, committed 512K, reserved 1114112K
[2024-07-05T22:33:44.584-0700][info][gc,heap,exit]   class space    used 7K, committed 128K, reserved 1048576K
```

上面是 GC(12) 和 GC(13) 事件的日志，我们可以看到 GC(12) 和之前的事件都比较相近，进行了一次 young GC ，而对于 GC(13) 事件，这里是执行了一次并发标记的操作。
经过多个阶段，包括 `Concurrent Clear Claimed Marks`、`Concurrent Scan Root Regions`、`Concurrent Mark From Roots`、`Concurrent Preclean`、`Pause Remark`、`Concurrent Rebuild Remembered Sets`、`Pause Cleanup` 和 `Concurrent Cleanup for Next Mark`。

对于堆内存是 16g 的情况，因为只执行了 young GC ，比较简单，下面我们来看看 将内存设置为 4G，停顿时间设置为 100ms，region 大小设置为 1M的实验结果。

```
[2024-07-05T05:37:22.547-0700][info][gc] Using G1
[2024-07-05T05:37:22.575-0700][info][gc,init] Version: 17.0.11+1-LTS (release)
[2024-07-05T05:37:22.575-0700][info][gc,init] CPUs: 4 total, 4 available
[2024-07-05T05:37:22.575-0700][info][gc,init] Memory: 15953M
[2024-07-05T05:37:22.575-0700][info][gc,init] Large Page Support: Disabled
[2024-07-05T05:37:22.575-0700][info][gc,init] NUMA Support: Disabled
[2024-07-05T05:37:22.575-0700][info][gc,init] Compressed Oops: Enabled (Zero based)
[2024-07-05T05:37:22.575-0700][info][gc,init] Heap Region Size: 1M
[2024-07-05T05:37:22.575-0700][info][gc,init] Heap Min Capacity: 4G
[2024-07-05T05:37:22.575-0700][info][gc,init] Heap Initial Capacity: 4G
[2024-07-05T05:37:22.575-0700][info][gc,init] Heap Max Capacity: 4G
[2024-07-05T05:37:22.575-0700][info][gc,init] Pre-touch: Disabled
[2024-07-05T05:37:22.575-0700][info][gc,init] Parallel Workers: 4
[2024-07-05T05:37:22.575-0700][info][gc,init] Concurrent Workers: 1
[2024-07-05T05:37:22.575-0700][info][gc,init] Concurrent Refinement Workers: 4
[2024-07-05T05:37:22.575-0700][info][gc,init] Periodic GC: Disabled
[2024-07-05T05:37:22.582-0700][info][gc,metaspace] CDS archive(s) mapped at: [0x00007f785f000000-0x00007f785fbbb000-0x00007f785fbbb000), size 12300288, SharedBaseAddress: 0x00007f785f000000, ArchiveRelocationMode: 1.
[2024-07-05T05:37:22.582-0700][info][gc,metaspace] Compressed class space mapped at: 0x00007f7860000000-0x00007f78a0000000, reserved size: 1073741824
[2024-07-05T05:37:22.582-0700][info][gc,metaspace] Narrow klass base: 0x00007f785f000000, Narrow klass shift: 0, Narrow klass range: 0x100000000
[2024-07-05T05:37:22.928-0700][info][gc,start    ] GC(0) Pause Young (Normal) (G1 Evacuation Pause)
[2024-07-05T05:37:22.929-0700][info][gc,task     ] GC(0) Using 4 workers of 4 for evacuation
[2024-07-05T05:37:22.973-0700][info][gc,phases   ] GC(0)   Pre Evacuate Collection Set: 0.4ms
[2024-07-05T05:37:22.973-0700][info][gc,phases   ] GC(0)   Merge Heap Roots: 0.2ms
[2024-07-05T05:37:22.973-0700][info][gc,phases   ] GC(0)   Evacuate Collection Set: 42.8ms
[2024-07-05T05:37:22.973-0700][info][gc,phases   ] GC(0)   Post Evacuate Collection Set: 0.8ms
[2024-07-05T05:37:22.973-0700][info][gc,phases   ] GC(0)   Other: 0.9ms
[2024-07-05T05:37:22.973-0700][info][gc,heap     ] GC(0) Eden regions: 204->0(178)
[2024-07-05T05:37:22.973-0700][info][gc,heap     ] GC(0) Survivor regions: 0->26(26)
[2024-07-05T05:37:22.973-0700][info][gc,heap     ] GC(0) Old regions: 0->78
[2024-07-05T05:37:22.973-0700][info][gc,heap     ] GC(0) Archive regions: 2->2
[2024-07-05T05:37:22.973-0700][info][gc,heap     ] GC(0) Humongous regions: 0->0
[2024-07-05T05:37:22.973-0700][info][gc,metaspace] GC(0) Metaspace: 177K(384K)->177K(384K) NonClass: 170K(256K)->170K(256K) Class: 6K(128K)->6K(128K)
[2024-07-05T05:37:22.973-0700][info][gc          ] GC(0) Pause Young (Normal) (G1 Evacuation Pause) 204M->104M(4096M) 45.226ms
[2024-07-05T05:37:22.973-0700][info][gc,cpu      ] GC(0) User=0.05s Sys=0.11s Real=0.04s
[2024-07-05T05:37:23.636-0700][info][gc,start    ] GC(1) Pause Young (Normal) (G1 Evacuation Pause)
[2024-07-05T05:37:23.636-0700][info][gc,task     ] GC(1) Using 4 workers of 4 for evacuation
[2024-07-05T05:37:23.719-0700][info][gc,phases   ] GC(1)   Pre Evacuate Collection Set: 0.4ms
[2024-07-05T05:37:23.719-0700][info][gc,phases   ] GC(1)   Merge Heap Roots: 0.3ms
[2024-07-05T05:37:23.719-0700][info][gc,phases   ] GC(1)   Evacuate Collection Set: 81.6ms
[2024-07-05T05:37:23.719-0700][info][gc,phases   ] GC(1)   Post Evacuate Collection Set: 0.5ms
[2024-07-05T05:37:23.719-0700][info][gc,phases   ] GC(1)   Other: 0.1ms
[2024-07-05T05:37:23.719-0700][info][gc,heap     ] GC(1) Eden regions: 178->0(178)
[2024-07-05T05:37:23.719-0700][info][gc,heap     ] GC(1) Survivor regions: 26->26(26)
[2024-07-05T05:37:23.719-0700][info][gc,heap     ] GC(1) Old regions: 78->223
[2024-07-05T05:37:23.719-0700][info][gc,heap     ] GC(1) Archive regions: 2->2
[2024-07-05T05:37:23.719-0700][info][gc,heap     ] GC(1) Humongous regions: 2->2
[2024-07-05T05:37:23.719-0700][info][gc,metaspace] GC(1) Metaspace: 188K(384K)->188K(384K) NonClass: 181K(256K)->181K(256K) Class: 6K(128K)->6K(128K)
[2024-07-05T05:37:23.719-0700][info][gc          ] GC(1) Pause Young (Normal) (G1 Evacuation Pause) 284M->251M(4096M) 83.010ms
[2024-07-05T05:37:23.719-0700][info][gc,cpu      ] GC(1) User=0.13s Sys=0.19s Real=0.08s
```

- 最开始的 gc,init 阶段还是说明了本次程序的一些参数配置。
- 下面的两个 GC(0) 和 GC(1) 阶段，还是两个 young GC 的过程，主要涉及了 Eden 区的垃圾回收。

```
[2024-07-05T05:38:34.803-0700][info][gc,start    ] GC(12) Pause Young (Concurrent Start) (G1 Evacuation Pause)
[2024-07-05T05:38:34.803-0700][info][gc,task     ] GC(12) Using 4 workers of 4 for evacuation
[2024-07-05T05:38:34.907-0700][info][gc,mmu      ] GC(12) MMU target violated: 101.0ms (100.0ms/101.0ms)
[2024-07-05T05:38:34.907-0700][info][gc,phases   ] GC(12)   Pre Evacuate Collection Set: 0.4ms
[2024-07-05T05:38:34.907-0700][info][gc,phases   ] GC(12)   Merge Heap Roots: 0.2ms
[2024-07-05T05:38:34.907-0700][info][gc,phases   ] GC(12)   Evacuate Collection Set: 102.7ms
[2024-07-05T05:38:34.907-0700][info][gc,phases   ] GC(12)   Post Evacuate Collection Set: 0.5ms
[2024-07-05T05:38:34.907-0700][info][gc,phases   ] GC(12)   Other: 0.4ms
[2024-07-05T05:38:34.907-0700][info][gc,heap     ] GC(12) Eden regions: 178->0(178)
[2024-07-05T05:38:34.907-0700][info][gc,heap     ] GC(12) Survivor regions: 26->26(26)
[2024-07-05T05:38:34.907-0700][info][gc,heap     ] GC(12) Old regions: 1877->2046
[2024-07-05T05:38:34.907-0700][info][gc,heap     ] GC(12) Archive regions: 2->2
[2024-07-05T05:38:34.907-0700][info][gc,heap     ] GC(12) Humongous regions: 16->16
[2024-07-05T05:38:34.907-0700][info][gc,metaspace] GC(12) Metaspace: 275K(512K)->275K(512K) NonClass: 268K(384K)->268K(384K) Class: 6K(128K)->6K(128K)
[2024-07-05T05:38:34.907-0700][info][gc          ] GC(12) Pause Young (Concurrent Start) (G1 Evacuation Pause) 2097M->2088M(4096M) 104.210ms
[2024-07-05T05:38:34.907-0700][info][gc,cpu      ] GC(12) User=0.19s Sys=0.21s Real=0.10s
[2024-07-05T05:38:34.907-0700][info][gc          ] GC(13) Concurrent Mark Cycle
[2024-07-05T05:38:34.907-0700][info][gc,marking  ] GC(13) Concurrent Clear Claimed Marks
[2024-07-05T05:38:34.907-0700][info][gc,marking  ] GC(13) Concurrent Clear Claimed Marks 0.008ms
[2024-07-05T05:38:34.907-0700][info][gc,marking  ] GC(13) Concurrent Scan Root Regions
[2024-07-05T05:38:34.917-0700][info][gc,marking  ] GC(13) Concurrent Scan Root Regions 9.545ms
[2024-07-05T05:38:34.917-0700][info][gc,marking  ] GC(13) Concurrent Mark
[2024-07-05T05:38:34.917-0700][info][gc,marking  ] GC(13) Concurrent Mark From Roots
[2024-07-05T05:38:34.917-0700][info][gc,task     ] GC(13) Using 1 workers of 1 for marking
[2024-07-05T05:38:35.192-0700][info][gc,marking  ] GC(13) Concurrent Mark From Roots 275.447ms
[2024-07-05T05:38:35.192-0700][info][gc,marking  ] GC(13) Concurrent Preclean
[2024-07-05T05:38:35.192-0700][info][gc,marking  ] GC(13) Concurrent Preclean 0.066ms
[2024-07-05T05:38:58.280-0700][info][gc,start    ] GC(13) Pause Remark
[2024-07-05T05:38:58.281-0700][info][gc          ] GC(13) Pause Remark 2088M->2076M(4096M) 1.604ms
[2024-07-05T05:38:58.281-0700][info][gc,cpu      ] GC(13) User=0.01s Sys=0.00s Real=0.00s
[2024-07-05T05:38:58.282-0700][info][gc,marking  ] GC(13) Concurrent Mark 23364.715ms
[2024-07-05T05:38:58.282-0700][info][gc,marking  ] GC(13) Concurrent Rebuild Remembered Sets
[2024-07-05T05:38:58.508-0700][info][gc,marking  ] GC(13) Concurrent Rebuild Remembered Sets 226.769ms
[2024-07-05T05:38:58.677-0700][info][gc,start    ] GC(13) Pause Cleanup
[2024-07-05T05:38:58.678-0700][info][gc          ] GC(13) Pause Cleanup 2086M->2086M(4096M) 1.380ms
[2024-07-05T05:38:58.678-0700][info][gc,cpu      ] GC(13) User=0.00s Sys=0.00s Real=0.00s
[2024-07-05T05:38:58.678-0700][info][gc,marking  ] GC(13) Concurrent Cleanup for Next Mark
[2024-07-05T05:38:58.713-0700][info][gc,marking  ] GC(13) Concurrent Cleanup for Next Mark 34.709ms
[2024-07-05T05:38:58.713-0700][info][gc          ] GC(13) Concurrent Mark Cycle 23805.698ms
[2024-07-05T05:39:09.985-0700][info][gc,start    ] GC(14) Pause Young (Prepare Mixed) (G1 Evacuation Pause)
[2024-07-05T05:39:09.985-0700][info][gc,task     ] GC(14) Using 4 workers of 4 for evacuation
[2024-07-05T05:39:10.128-0700][info][gc,mmu      ] GC(14) MMU target violated: 101.0ms (100.0ms/101.0ms)
[2024-07-05T05:39:10.128-0700][info][gc,phases   ] GC(14)   Pre Evacuate Collection Set: 0.8ms
[2024-07-05T05:39:10.128-0700][info][gc,phases   ] GC(14)   Merge Heap Roots: 0.3ms
[2024-07-05T05:39:10.128-0700][info][gc,phases   ] GC(14)   Evacuate Collection Set: 140.4ms
[2024-07-05T05:39:10.128-0700][info][gc,phases   ] GC(14)   Post Evacuate Collection Set: 0.9ms
[2024-07-05T05:39:10.128-0700][info][gc,phases   ] GC(14)   Other: 0.3ms
[2024-07-05T05:39:10.128-0700][info][gc,heap     ] GC(14) Eden regions: 178->0(178)
[2024-07-05T05:39:10.128-0700][info][gc,heap     ] GC(14) Survivor regions: 26->26(26)
[2024-07-05T05:39:10.128-0700][info][gc,heap     ] GC(14) Old regions: 2045->2221
[2024-07-05T05:39:10.128-0700][info][gc,heap     ] GC(14) Archive regions: 2->2
[2024-07-05T05:39:10.128-0700][info][gc,heap     ] GC(14) Humongous regions: 12->12
[2024-07-05T05:39:10.128-0700][info][gc,metaspace] GC(14) Metaspace: 279K(512K)->279K(512K) NonClass: 272K(384K)->272K(384K) Class: 6K(128K)->6K(128K)
[2024-07-05T05:39:10.128-0700][info][gc          ] GC(14) Pause Young (Prepare Mixed) (G1 Evacuation Pause) 2261M->2259M(4096M) 142.831ms
[2024-07-05T05:39:10.128-0700][info][gc,cpu      ] GC(14) User=0.25s Sys=0.28s Real=0.14s
[2024-07-05T05:39:26.893-0700][info][gc,start    ] GC(15) Pause Young (Mixed) (G1 Evacuation Pause)
[2024-07-05T05:39:26.893-0700][info][gc,task     ] GC(15) Using 4 workers of 4 for evacuation
[2024-07-05T05:39:27.014-0700][info][gc,mmu      ] GC(15) MMU target violated: 101.0ms (100.0ms/101.0ms)
[2024-07-05T05:39:27.014-0700][info][gc,phases   ] GC(15)   Pre Evacuate Collection Set: 0.4ms
[2024-07-05T05:39:27.014-0700][info][gc,phases   ] GC(15)   Merge Heap Roots: 0.2ms
[2024-07-05T05:39:27.014-0700][info][gc,phases   ] GC(15)   Evacuate Collection Set: 119.5ms
[2024-07-05T05:39:27.014-0700][info][gc,phases   ] GC(15)   Post Evacuate Collection Set: 0.6ms
[2024-07-05T05:39:27.014-0700][info][gc,phases   ] GC(15)   Other: 0.2ms
[2024-07-05T05:39:27.014-0700][info][gc,heap     ] GC(15) Eden regions: 178->0(178)
[2024-07-05T05:39:27.014-0700][info][gc,heap     ] GC(15) Survivor regions: 26->26(26)
[2024-07-05T05:39:27.014-0700][info][gc,heap     ] GC(15) Old regions: 2221->2285
[2024-07-05T05:39:27.014-0700][info][gc,heap     ] GC(15) Archive regions: 2->2
[2024-07-05T05:39:27.014-0700][info][gc,heap     ] GC(15) Humongous regions: 12->12
[2024-07-05T05:39:27.014-0700][info][gc,metaspace] GC(15) Metaspace: 287K(512K)->287K(512K) NonClass: 281K(384K)->281K(384K) Class: 6K(128K)->6K(128K)
[2024-07-05T05:39:27.014-0700][info][gc          ] GC(15) Pause Young (Mixed) (G1 Evacuation Pause) 2437M->2322M(4096M) 121.060ms
[2024-07-05T05:39:27.014-0700][info][gc,cpu      ] GC(15) User=0.21s Sys=0.24s Real=0.13s
```

- 从GC(12)开始，垃圾收集模式与之前的有所不同，GC(12) 是“Pause Young (Concurrent Start) (G1 Evacuation Pause)”，表示这次GC是一个年轻代垃圾收集（Young GC）事件，同时启动了并发标记周期（Concurrent Mark Cycle）。主要目的是清理年轻代区域的内存，同时启动一个新的并发标记周期。这个标记周期用于标记堆中的活动对象，以便在后续的混合收集中更有效地回收老年代的内存。
- 对于 GC(13) 事件，开始了并发标记周期，这是G1收集器的一个重要特性，用于标记堆中的活动对象。
- GC(14) 是“Pause Young (Prepare Mixed) (G1 Evacuation Pause)”，表示这是一次准备混合收集（Prepare Mixed）暂停，仍然是年轻代垃圾收集。除了清理年轻代区域外，这次GC还为即将到来的混合收集做准备。混合收集不仅包括年轻代区域，还包括一些老年代区域，这通常在并发标记周期完成后进行。
- GC(15) 是“Pause Young (Mixed) (G1 Evacuation Pause)”，表示这是一次混合垃圾收集（Mixed GC），包括年轻代和部分老年代的内存回收。清理年轻代区域的同时，也回收了标记为垃圾的老年代对象。这种类型的GC通常会在并发标记周期完成后进行，以最大化内存回收效率。

从 GC(15) 到 GC(22) 都是执行了混合垃圾回收，然后就开始重复 GC(12)~GC(22) 的过程。

```
[2024-07-05T06:00:01.561-0700][info][gc,start    ] GC(58) Pause Young (Concurrent Start) (G1 Preventive Collection)
[2024-07-05T06:00:01.561-0700][info][gc,task     ] GC(58) Using 4 workers of 4 for evacuation
[2024-07-05T06:00:01.572-0700][info][gc,phases   ] GC(58)   Pre Evacuate Collection Set: 0.9ms
[2024-07-05T06:00:01.572-0700][info][gc,phases   ] GC(58)   Merge Heap Roots: 0.3ms
[2024-07-05T06:00:01.572-0700][info][gc,phases   ] GC(58)   Evacuate Collection Set: 8.6ms
[2024-07-05T06:00:01.572-0700][info][gc,phases   ] GC(58)   Post Evacuate Collection Set: 0.6ms
[2024-07-05T06:00:01.572-0700][info][gc,phases   ] GC(58)   Other: 0.4ms
[2024-07-05T06:00:01.572-0700][info][gc,heap     ] GC(58) Eden regions: 15->0(188)
[2024-07-05T06:00:01.572-0700][info][gc,heap     ] GC(58) Survivor regions: 0->16(18)
[2024-07-05T06:00:01.572-0700][info][gc,heap     ] GC(58) Old regions: 4045->4045
[2024-07-05T06:00:01.572-0700][info][gc,heap     ] GC(58) Archive regions: 2->2
[2024-07-05T06:00:01.572-0700][info][gc,heap     ] GC(58) Humongous regions: 16->16
[2024-07-05T06:00:01.572-0700][info][gc,metaspace] GC(58) Metaspace: 298K(512K)->298K(512K) NonClass: 291K(384K)->291K(384K) Class: 7K(128K)->7K(128K)
[2024-07-05T06:00:01.572-0700][info][gc          ] GC(58) Pause Young (Concurrent Start) (G1 Preventive Collection) 4076M->4077M(4096M) 10.878ms
[2024-07-05T06:00:01.572-0700][info][gc,cpu      ] GC(58) User=0.03s Sys=0.00s Real=0.01s
[2024-07-05T06:00:01.572-0700][info][gc          ] GC(59) Concurrent Mark Cycle
[2024-07-05T06:00:01.572-0700][info][gc,marking  ] GC(59) Concurrent Clear Claimed Marks
[2024-07-05T06:00:01.572-0700][info][gc,marking  ] GC(59) Concurrent Clear Claimed Marks 0.011ms
[2024-07-05T06:00:01.572-0700][info][gc,marking  ] GC(59) Concurrent Scan Root Regions
[2024-07-05T06:00:01.573-0700][info][gc,marking  ] GC(59) Concurrent Scan Root Regions 1.087ms
[2024-07-05T06:00:01.573-0700][info][gc,marking  ] GC(59) Concurrent Mark
[2024-07-05T06:00:01.573-0700][info][gc,marking  ] GC(59) Concurrent Mark From Roots
[2024-07-05T06:00:01.573-0700][info][gc,task     ] GC(59) Using 1 workers of 1 for marking
[2024-07-05T06:00:01.573-0700][info][gc,start    ] GC(60) Pause Young (Normal) (G1 Preventive Collection)
[2024-07-05T06:00:01.573-0700][info][gc,task     ] GC(60) Using 4 workers of 4 for evacuation
[2024-07-05T06:00:01.582-0700][info][gc,phases   ] GC(60)   Pre Evacuate Collection Set: 1.4ms
[2024-07-05T06:00:01.582-0700][info][gc,phases   ] GC(60)   Merge Heap Roots: 0.1ms
[2024-07-05T06:00:01.582-0700][info][gc,phases   ] GC(60)   Evacuate Collection Set: 7.6ms
[2024-07-05T06:00:01.582-0700][info][gc,phases   ] GC(60)   Post Evacuate Collection Set: 0.6ms
[2024-07-05T06:00:01.583-0700][info][gc,phases   ] GC(60)   Other: 0.1ms
[2024-07-05T06:00:01.583-0700][info][gc,heap     ] GC(60) Eden regions: 1->0(203)
[2024-07-05T06:00:01.583-0700][info][gc,heap     ] GC(60) Survivor regions: 16->1(16)
[2024-07-05T06:00:01.583-0700][info][gc,heap     ] GC(60) Old regions: 4045->4060
[2024-07-05T06:00:01.583-0700][info][gc,heap     ] GC(60) Archive regions: 2->2
[2024-07-05T06:00:01.583-0700][info][gc,heap     ] GC(60) Humongous regions: 16->16
[2024-07-05T06:00:01.583-0700][info][gc,metaspace] GC(60) Metaspace: 298K(512K)->298K(512K) NonClass: 291K(384K)->291K(384K) Class: 7K(128K)->7K(128K)
[2024-07-05T06:00:01.583-0700][info][gc          ] GC(60) Pause Young (Normal) (G1 Preventive Collection) 4078M->4077M(4096M) 9.129ms
[2024-07-05T06:00:01.583-0700][info][gc,cpu      ] GC(60) User=0.02s Sys=0.01s Real=0.02s
[2024-07-05T06:00:02.446-0700][info][gc,marking  ] GC(59) Concurrent Mark From Roots 872.540ms
[2024-07-05T06:00:02.446-0700][info][gc,marking  ] GC(59) Concurrent Preclean
[2024-07-05T06:00:02.446-0700][info][gc,marking  ] GC(59) Concurrent Preclean 0.315ms
[2024-07-05T06:00:12.747-0700][info][gc,start    ] GC(59) Pause Remark
[2024-07-05T06:00:12.751-0700][info][gc          ] GC(59) Pause Remark 4081M->4081M(4096M) 3.865ms
[2024-07-05T06:00:12.751-0700][info][gc,cpu      ] GC(59) User=0.00s Sys=0.00s Real=0.00s
[2024-07-05T06:00:12.752-0700][info][gc,marking  ] GC(59) Concurrent Mark 11178.393ms
[2024-07-05T06:00:12.752-0700][info][gc,marking  ] GC(59) Concurrent Rebuild Remembered Sets
[2024-07-05T06:00:13.297-0700][info][gc,marking  ] GC(59) Concurrent Rebuild Remembered Sets 545.120ms
[2024-07-05T06:00:15.712-0700][info][gc,start    ] GC(59) Pause Cleanup
[2024-07-05T06:00:15.714-0700][info][gc          ] GC(59) Pause Cleanup 4081M->4081M(4096M) 2.088ms
[2024-07-05T06:00:15.714-0700][info][gc,cpu      ] GC(59) User=0.00s Sys=0.00s Real=0.01s
[2024-07-05T06:00:15.719-0700][info][gc,marking  ] GC(59) Concurrent Cleanup for Next Mark
[2024-07-05T06:00:15.731-0700][info][gc,marking  ] GC(59) Concurrent Cleanup for Next Mark 12.760ms
[2024-07-05T06:00:15.731-0700][info][gc          ] GC(59) Concurrent Mark Cycle 14159.146ms
[2024-07-05T06:00:16.706-0700][info][gc,start    ] GC(61) Pause Young (Prepare Mixed) (G1 Preventive Collection)
[2024-07-05T06:00:16.706-0700][info][gc,task     ] GC(61) Using 4 workers of 4 for evacuation
[2024-07-05T06:00:16.719-0700][info][gc,phases   ] GC(61)   Pre Evacuate Collection Set: 0.7ms
[2024-07-05T06:00:16.719-0700][info][gc,phases   ] GC(61)   Merge Heap Roots: 0.4ms
[2024-07-05T06:00:16.719-0700][info][gc,phases   ] GC(61)   Evacuate Collection Set: 11.0ms
[2024-07-05T06:00:16.719-0700][info][gc,phases   ] GC(61)   Post Evacuate Collection Set: 0.6ms
[2024-07-05T06:00:16.719-0700][info][gc,phases   ] GC(61)   Other: 0.1ms
[2024-07-05T06:00:16.719-0700][info][gc,heap     ] GC(61) Eden regions: 7->0(195)
[2024-07-05T06:00:16.719-0700][info][gc,heap     ] GC(61) Survivor regions: 1->9(10)
[2024-07-05T06:00:16.719-0700][info][gc,heap     ] GC(61) Old regions: 4060->4060
[2024-07-05T06:00:16.719-0700][info][gc,heap     ] GC(61) Archive regions: 2->2
[2024-07-05T06:00:16.719-0700][info][gc,heap     ] GC(61) Humongous regions: 16->16
[2024-07-05T06:00:16.719-0700][info][gc,metaspace] GC(61) Metaspace: 298K(512K)->298K(512K) NonClass: 291K(384K)->291K(384K) Class: 7K(128K)->7K(128K)
[2024-07-05T06:00:16.719-0700][info][gc          ] GC(61) Pause Young (Prepare Mixed) (G1 Preventive Collection) 4084M->4085M(4096M) 13.005ms
[2024-07-05T06:00:16.719-0700][info][gc,cpu      ] GC(61) User=0.04s Sys=0.00s Real=0.01s
```

- GC(59)代表并发标记周期的一部分。并发标记周期本质上是一个长时间的过程，分为几个阶段：
    1. Concurrent Clear Claimed Marks：清除以前标记的对象。
    2. Concurrent Scan Root Regions：扫描根区域。
    3. Concurrent Mark：并发标记存活对象。
    4. Concurrent Preclean：预清理阶段。
    5. Pause Remark：短暂停止，完成标记工作。
    6. Concurrent Rebuild Remembered Sets：重建记忆集。
    7. Pause Cleanup：短暂停止，进行清理工作。
    8. Concurrent Cleanup for Next Mark：为下一个标记周期做准备。
- 而在进行 GC(60) 的时候，GC(59) 也是同步执行的。

```
[2024-07-05T06:00:26.076-0700][info][gc,task     ] GC(67) Using 4 workers of 4 for evacuation
[2024-07-05T06:00:26.127-0700][info][gc          ] GC(67) To-space exhausted
[2024-07-05T06:00:26.127-0700][info][gc,phases   ] GC(67)   Pre Evacuate Collection Set: 1.3ms
[2024-07-05T06:00:26.127-0700][info][gc,phases   ] GC(67)   Merge Heap Roots: 0.3ms
[2024-07-05T06:00:26.127-0700][info][gc,phases   ] GC(67)   Evacuate Collection Set: 37.1ms
[2024-07-05T06:00:26.127-0700][info][gc,phases   ] GC(67)   Post Evacuate Collection Set: 12.5ms
[2024-07-05T06:00:26.127-0700][info][gc,phases   ] GC(67)   Other: 0.4ms
[2024-07-05T06:00:26.127-0700][info][gc,heap     ] GC(67) Eden regions: 1->0(204)
[2024-07-05T06:00:26.127-0700][info][gc,heap     ] GC(67) Survivor regions: 4->0(0)
[2024-07-05T06:00:26.127-0700][info][gc,heap     ] GC(67) Old regions: 4073->4078
[2024-07-05T06:00:26.127-0700][info][gc,heap     ] GC(67) Archive regions: 2->2
[2024-07-05T06:00:26.128-0700][info][gc,heap     ] GC(67) Humongous regions: 16->16
[2024-07-05T06:00:26.128-0700][info][gc,metaspace] GC(67) Metaspace: 298K(512K)->298K(512K) NonClass: 291K(384K)->291K(384K) Class: 7K(128K)->7K(128K)
[2024-07-05T06:00:26.128-0700][info][gc          ] GC(67) Pause Young (Mixed) (G1 Evacuation Pause) 4094M->4094M(4096M) 51.744ms
[2024-07-05T06:00:26.128-0700][info][gc,cpu      ] GC(67) User=0.17s Sys=0.02s Real=0.05s
[2024-07-05T06:00:26.128-0700][info][gc,ergo     ] Attempting full compaction
[2024-07-05T06:00:26.128-0700][info][gc,task     ] GC(68) Using 4 workers of 4 for full compaction
[2024-07-05T06:00:26.128-0700][info][gc,start    ] GC(68) Pause Full (G1 Compaction Pause)
[2024-07-05T06:00:26.129-0700][info][gc,phases,start] GC(68) Phase 1: Mark live objects
[2024-07-05T06:00:26.309-0700][info][gc,phases      ] GC(68) Phase 1: Mark live objects 180.020ms
[2024-07-05T06:00:26.309-0700][info][gc,phases,start] GC(68) Phase 2: Prepare for compaction
[2024-07-05T06:00:26.438-0700][info][gc,phases      ] GC(68) Phase 2: Prepare for compaction 129.205ms
[2024-07-05T06:00:26.438-0700][info][gc,phases,start] GC(68) Phase 3: Adjust pointers
[2024-07-05T06:00:26.572-0700][info][gc,phases      ] GC(68) Phase 3: Adjust pointers 134.423ms
[2024-07-05T06:00:26.572-0700][info][gc,phases,start] GC(68) Phase 4: Compact heap
[2024-07-05T06:00:27.089-0700][info][gc,phases      ] GC(68) Phase 4: Compact heap 516.928ms
[2024-07-05T06:00:27.095-0700][info][gc,heap        ] GC(68) Eden regions: 0->0(204)
[2024-07-05T06:00:27.095-0700][info][gc,heap        ] GC(68) Survivor regions: 0->0(0)
[2024-07-05T06:00:27.095-0700][info][gc,heap        ] GC(68) Old regions: 4078->3404
[2024-07-05T06:00:27.095-0700][info][gc,heap        ] GC(68) Archive regions: 2->2
[2024-07-05T06:00:27.095-0700][info][gc,heap        ] GC(68) Humongous regions: 16->16
[2024-07-05T06:00:27.095-0700][info][gc,metaspace   ] GC(68) Metaspace: 298K(512K)->298K(512K) NonClass: 291K(384K)->291K(384K) Class: 7K(128K)->7K(128K)
[2024-07-05T06:00:27.095-0700][info][gc             ] GC(68) Pause Full (G1 Compaction Pause) 4094M->3418M(4096M) 967.422ms
[2024-07-05T06:00:27.096-0700][info][gc,cpu         ] GC(68) User=3.82s Sys=0.00s Real=0.97s
[2024-07-05T06:01:38.021-0700][info][gc,start       ] GC(69) Pause Young (Normal) (G1 Evacuation Pause)
[2024-07-05T06:01:38.021-0700][info][gc,task        ] GC(69) Using 4 workers of 4 for evacuation
[2024-07-05T06:01:38.127-0700][info][gc,mmu         ] GC(69) MMU target violated: 101.0ms (100.0ms/101.0ms)
[2024-07-05T06:01:38.127-0700][info][gc,phases      ] GC(69)   Pre Evacuate Collection Set: 0.5ms
[2024-07-05T06:01:38.127-0700][info][gc,phases      ] GC(69)   Merge Heap Roots: 0.3ms
[2024-07-05T06:01:38.127-0700][info][gc,phases      ] GC(69)   Evacuate Collection Set: 103.0ms
[2024-07-05T06:01:38.127-0700][info][gc,phases      ] GC(69)   Post Evacuate Collection Set: 1.9ms
[2024-07-05T06:01:38.127-0700][info][gc,phases      ] GC(69)   Other: 0.4ms
[2024-07-05T06:01:38.127-0700][info][gc,heap        ] GC(69) Eden regions: 204->0(178)
[2024-07-05T06:01:38.127-0700][info][gc,heap        ] GC(69) Survivor regions: 0->26(26)
[2024-07-05T06:01:38.127-0700][info][gc,heap        ] GC(69) Old regions: 3404->3579
[2024-07-05T06:01:38.127-0700][info][gc,heap        ] GC(69) Archive regions: 2->2
[2024-07-05T06:01:38.127-0700][info][gc,heap        ] GC(69) Humongous regions: 16->16
[2024-07-05T06:01:38.127-0700][info][gc,metaspace   ] GC(69) Metaspace: 298K(512K)->298K(512K) NonClass: 291K(384K)->291K(384K) Class: 7K(128K)->7K(128K)
[2024-07-05T06:01:38.127-0700][info][gc             ] GC(69) Pause Young (Normal) (G1 Evacuation Pause) 3622M->3619M(4096M) 106.213ms
[2024-07-05T06:01:38.127-0700][info][gc,cpu         ] GC(69) User=0.29s Sys=0.07s Real=0.11s
[2024-07-05T06:02:59.673-0700][info][gc,start       ] GC(70) Pause Young (Concurrent Start) (G1 Evacuation Pause)
[2024-07-05T06:02:59.673-0700][info][gc,task        ] GC(70) Using 4 workers of 4 for evacuation
[2024-07-05T06:02:59.762-0700][info][gc,phases      ] GC(70)   Pre Evacuate Collection Set: 0.5ms
[2024-07-05T06:02:59.762-0700][info][gc,phases      ] GC(70)   Merge Heap Roots: 0.4ms
[2024-07-05T06:02:59.762-0700][info][gc,phases      ] GC(70)   Evacuate Collection Set: 87.0ms
[2024-07-05T06:02:59.762-0700][info][gc,phases      ] GC(70)   Post Evacuate Collection Set: 0.8ms
[2024-07-05T06:02:59.762-0700][info][gc,phases      ] GC(70)   Other: 0.5ms
[2024-07-05T06:02:59.762-0700][info][gc,heap        ] GC(70) Eden regions: 178->0(178)
[2024-07-05T06:02:59.762-0700][info][gc,heap        ] GC(70) Survivor regions: 26->26(26)
[2024-07-05T06:02:59.762-0700][info][gc,heap        ] GC(70) Old regions: 3579->3754
[2024-07-05T06:02:59.762-0700][info][gc,heap        ] GC(70) Archive regions: 2->2
[2024-07-05T06:02:59.762-0700][info][gc,heap        ] GC(70) Humongous regions: 16->16
[2024-07-05T06:02:59.762-0700][info][gc,metaspace   ] GC(70) Metaspace: 298K(512K)->298K(512K) NonClass: 291K(384K)->291K(384K) Class: 7K(128K)->7K(128K)
[2024-07-05T06:02:59.762-0700][info][gc             ] GC(70) Pause Young (Concurrent Start) (G1 Evacuation Pause) 3797M->3793M(4096M) 89.244ms
[2024-07-05T06:02:59.762-0700][info][gc,cpu         ] GC(70) User=0.25s Sys=0.05s Real=0.09s
[2024-07-05T06:02:59.762-0700][info][gc             ] GC(71) Concurrent Mark Cycle
[2024-07-05T06:02:59.762-0700][info][gc,marking     ] GC(71) Concurrent Clear Claimed Marks
[2024-07-05T06:02:59.762-0700][info][gc,marking     ] GC(71) Concurrent Clear Claimed Marks 0.010ms
[2024-07-05T06:02:59.762-0700][info][gc,marking     ] GC(71) Concurrent Scan Root Regions
[2024-07-05T06:02:59.778-0700][info][gc,marking     ] GC(71) Concurrent Scan Root Regions 16.013ms
[2024-07-05T06:02:59.778-0700][info][gc,marking     ] GC(71) Concurrent Mark
[2024-07-05T06:02:59.778-0700][info][gc,marking     ] GC(71) Concurrent Mark From Roots
[2024-07-05T06:02:59.778-0700][info][gc,task        ] GC(71) Using 1 workers of 1 for marking
[2024-07-05T06:03:00.677-0700][info][gc,marking     ] GC(71) Concurrent Mark From Roots 898.393ms
[2024-07-05T06:03:00.677-0700][info][gc,marking     ] GC(71) Concurrent Preclean
[2024-07-05T06:03:00.677-0700][info][gc,marking     ] GC(71) Concurrent Preclean 0.092ms
[2024-07-05T06:03:14.823-0700][info][gc,start       ] GC(71) Pause Remark
[2024-07-05T06:03:14.825-0700][info][gc             ] GC(71) Pause Remark 3798M->3798M(4096M) 1.763ms
[2024-07-05T06:03:14.825-0700][info][gc,cpu         ] GC(71) User=0.01s Sys=0.00s Real=0.00s
[2024-07-05T06:03:14.826-0700][info][gc,marking     ] GC(71) Concurrent Mark 15048.141ms
[2024-07-05T06:03:14.826-0700][info][gc,marking     ] GC(71) Concurrent Rebuild Remembered Sets
[2024-07-05T06:03:15.247-0700][info][gc,marking     ] GC(71) Concurrent Rebuild Remembered Sets 420.578ms
[2024-07-05T06:03:16.077-0700][info][gc,start       ] GC(71) Pause Cleanup
[2024-07-05T06:03:16.079-0700][info][gc             ] GC(71) Pause Cleanup 3798M->3798M(4096M) 2.332ms
[2024-07-05T06:03:16.079-0700][info][gc,cpu         ] GC(71) User=0.00s Sys=0.01s Real=0.00s
[2024-07-05T06:03:16.079-0700][info][gc,marking     ] GC(71) Concurrent Cleanup for Next Mark
[2024-07-05T06:03:16.089-0700][info][gc,marking     ] GC(71) Concurrent Cleanup for Next Mark 10.120ms
[2024-07-05T06:03:16.090-0700][info][gc             ] GC(71) Concurrent Mark Cycle 16327.523ms
```

- GC(67) 阶段进行了一次年轻代和部分老年代的混合GC，同时尝试了完整的堆压缩。堆压缩时间较长，明显减少了老年代的碎片化。
- GC(68) 阶段主要集中在堆压缩上，显著减少了老年代的区域数量。
- GC(69) 和 GC(70) 阶段主要进行了新生代GC。
- GC(71) 阶段的并发标记周期时间非常长，可能由于堆内存的复杂性和对象的数量。标记过程和清理过程占用了大量时间。

接下来的GC过程，都是上述过程的重复。本次的 GC 一共执行了209 次GC。

### ZGC 日志举例分析

```
[2024-07-07T08:16:30.142-0700][info][gc,init] Initializing The Z Garbage Collector
[2024-07-07T08:16:30.142-0700][info][gc,init] Version: 17.0.11+1-LTS (release)
[2024-07-07T08:16:30.142-0700][info][gc,init] NUMA Support: Disabled
[2024-07-07T08:16:30.142-0700][info][gc,init] CPUs: 4 total, 4 available
[2024-07-07T08:16:30.142-0700][info][gc,init] Memory: 15953M
[2024-07-07T08:16:30.143-0700][info][gc,init] Large Page Support: Disabled
[2024-07-07T08:16:30.143-0700][info][gc,init] GC Workers: 1 (dynamic)
[2024-07-07T08:16:30.145-0700][info][gc,init] Address Space Type: Contiguous/Unrestricted/Complete
[2024-07-07T08:16:30.145-0700][info][gc,init] Address Space Size: 65536M x 3 = 196608M
[2024-07-07T08:16:30.145-0700][info][gc,init] Heap Backing File: /memfd:java_heap
[2024-07-07T08:16:30.146-0700][info][gc,init] Heap Backing Filesystem: tmpfs (0x1021994)
[2024-07-07T08:16:30.147-0700][info][gc,init] Min Capacity: 4096M
[2024-07-07T08:16:30.147-0700][info][gc,init] Initial Capacity: 4096M
[2024-07-07T08:16:30.147-0700][info][gc,init] Max Capacity: 4096M
[2024-07-07T08:16:30.148-0700][info][gc,init] Medium Page Size: 32M
[2024-07-07T08:16:30.148-0700][info][gc,init] Pre-touch: Disabled
[2024-07-07T08:16:30.148-0700][info][gc,init] Available space on backing filesystem: N/A
[2024-07-07T08:16:30.148-0700][info][gc,init] Uncommit: Implicitly Disabled (-Xms equals -Xmx)
[2024-07-07T08:16:31.254-0700][info][gc,init] Runtime Workers: 3
[2024-07-07T08:16:31.257-0700][info][gc     ] Using The Z Garbage Collector
[2024-07-07T08:16:31.290-0700][info][gc,metaspace] CDS archive(s) mapped at: [0x00007fc43b000000-0x00007fc43bb93000-0x00007fc43bb93000), size 12136448, SharedBaseAddress: 0x00007fc43b000000, ArchiveRelocationMode: 1.
[2024-07-07T08:16:31.290-0700][info][gc,metaspace] Compressed class space mapped at: 0x00007fc43c000000-0x00007fc47c000000, reserved size: 1073741824
[2024-07-07T08:16:31.290-0700][info][gc,metaspace] Narrow klass base: 0x00007fc43b000000, Narrow klass shift: 0, Narrow klass range: 0x100000000
[2024-07-07T08:16:31.420-0700][info][gc,start    ] GC(0) Garbage Collection (Timer)
[2024-07-07T08:16:31.420-0700][info][gc,task     ] GC(0) Using 1 workers
[2024-07-07T08:16:31.421-0700][info][gc,phases   ] GC(0) Pause Mark Start 0.092ms
[2024-07-07T08:16:31.429-0700][info][gc,phases   ] GC(0) Concurrent Mark 7.651ms
[2024-07-07T08:16:31.429-0700][info][gc,phases   ] GC(0) Pause Mark End 0.094ms
[2024-07-07T08:16:31.430-0700][info][gc,phases   ] GC(0) Concurrent Mark Free 0.018ms
[2024-07-07T08:16:31.432-0700][info][gc,phases   ] GC(0) Concurrent Process Non-Strong References 2.388ms
[2024-07-07T08:16:31.432-0700][info][gc,phases   ] GC(0) Concurrent Reset Relocation Set 0.017ms
[2024-07-07T08:16:31.480-0700][info][gc,phases   ] GC(0) Concurrent Select Relocation Set 47.741ms
[2024-07-07T08:16:31.481-0700][info][gc,phases   ] GC(0) Pause Relocate Start 0.096ms
[2024-07-07T08:16:31.482-0700][info][gc,phases   ] GC(0) Concurrent Relocate 1.016ms
[2024-07-07T08:16:31.482-0700][info][gc,load     ] GC(0) Load: 0.02/0.07/0.06
[2024-07-07T08:16:31.482-0700][info][gc,mmu      ] GC(0) MMU: 2ms/95.2%, 5ms/98.1%, 10ms/98.1%, 20ms/99.1%, 50ms/99.6%, 100ms/99.7%
[2024-07-07T08:16:31.482-0700][info][gc,marking  ] GC(0) Mark: 1 stripe(s), 2 proactive flush(es), 1 terminate flush(es), 0 completion(s), 0 continuation(s) 
[2024-07-07T08:16:31.482-0700][info][gc,marking  ] GC(0) Mark Stack Usage: 32M
[2024-07-07T08:16:31.482-0700][info][gc,nmethod  ] GC(0) NMethods: 108 registered, 0 unregistered
[2024-07-07T08:16:31.482-0700][info][gc,metaspace] GC(0) Metaspace: 0M used, 0M committed, 1088M reserved
[2024-07-07T08:16:31.482-0700][info][gc,ref      ] GC(0) Soft: 29 encountered, 0 discovered, 0 enqueued
[2024-07-07T08:16:31.482-0700][info][gc,ref      ] GC(0) Weak: 120 encountered, 24 discovered, 24 enqueued
[2024-07-07T08:16:31.483-0700][info][gc,ref      ] GC(0) Final: 0 encountered, 0 discovered, 0 enqueued
[2024-07-07T08:16:31.483-0700][info][gc,ref      ] GC(0) Phantom: 5 encountered, 4 discovered, 4 enqueued
[2024-07-07T08:16:31.483-0700][info][gc,reloc    ] GC(0) Small Pages: 7 / 14M, Empty: 0M, Relocated: 0M, In-Place: 0
[2024-07-07T08:16:31.483-0700][info][gc,reloc    ] GC(0) Medium Pages: 0 / 0M, Empty: 0M, Relocated: 0M, In-Place: 0
[2024-07-07T08:16:31.483-0700][info][gc,reloc    ] GC(0) Large Pages: 0 / 0M, Empty: 0M, Relocated: 0M, In-Place: 0
[2024-07-07T08:16:31.483-0700][info][gc,reloc    ] GC(0) Forwarding Usage: 0M
[2024-07-07T08:16:31.483-0700][info][gc,heap     ] GC(0) Min Capacity: 4096M(100%)
[2024-07-07T08:16:31.483-0700][info][gc,heap     ] GC(0) Max Capacity: 4096M(100%)
[2024-07-07T08:16:31.483-0700][info][gc,heap     ] GC(0) Soft Max Capacity: 4096M(100%)
[2024-07-07T08:16:31.483-0700][info][gc,heap     ] GC(0)                Mark Start          Mark End        Relocate Start      Relocate End           High               Low         
[2024-07-07T08:16:31.483-0700][info][gc,heap     ] GC(0)  Capacity:     4096M (100%)       4096M (100%)       4096M (100%)       4096M (100%)       4096M (100%)       4096M (100%)   
[2024-07-07T08:16:31.483-0700][info][gc,heap     ] GC(0)      Free:     4082M (100%)       4078M (100%)       4068M (99%)        4070M (99%)        4082M (100%)       4066M (99%)    
[2024-07-07T08:16:31.483-0700][info][gc,heap     ] GC(0)      Used:       14M (0%)           18M (0%)           28M (1%)           26M (1%)           30M (1%)           14M (0%)     
[2024-07-07T08:16:31.483-0700][info][gc,heap     ] GC(0)      Live:         -                10M (0%)           10M (0%)           10M (0%)             -                  -          
[2024-07-07T08:16:31.483-0700][info][gc,heap     ] GC(0) Allocated:         -                 4M (0%)           14M (0%)           13M (0%)             -                  -          
[2024-07-07T08:16:31.483-0700][info][gc,heap     ] GC(0)   Garbage:         -                 3M (0%)            3M (0%)            2M (0%)             -                  -          
[2024-07-07T08:16:31.483-0700][info][gc,heap     ] GC(0) Reclaimed:         -                  -                 0M (0%)            1M (0%)             -                  -          
[2024-07-07T08:16:31.483-0700][info][gc          ] GC(0) Garbage Collection (Timer) 14M(0%)->26M(1%)
[2024-07-07T08:16:31.620-0700][info][gc,start    ] GC(1) Garbage Collection (Timer)
[2024-07-07T08:16:31.620-0700][info][gc,task     ] GC(1) Using 1 workers
[2024-07-07T08:16:31.620-0700][info][gc,phases   ] GC(1) Pause Mark Start 0.096ms
[2024-07-07T08:16:31.639-0700][info][gc,phases   ] GC(1) Concurrent Mark 18.756ms
[2024-07-07T08:16:31.640-0700][info][gc,phases   ] GC(1) Pause Mark End 0.111ms
[2024-07-07T08:16:31.640-0700][info][gc,phases   ] GC(1) Concurrent Mark Free 0.020ms
[2024-07-07T08:16:31.642-0700][info][gc,phases   ] GC(1) Concurrent Process Non-Strong References 1.971ms
[2024-07-07T08:16:31.642-0700][info][gc,phases   ] GC(1) Concurrent Reset Relocation Set 0.019ms
[2024-07-07T08:16:31.646-0700][info][gc,phases   ] GC(1) Concurrent Select Relocation Set 3.124ms
[2024-07-07T08:16:31.646-0700][info][gc,phases   ] GC(1) Pause Relocate Start 0.095ms
[2024-07-07T08:16:31.658-0700][info][gc,phases   ] GC(1) Concurrent Relocate 11.075ms
[2024-07-07T08:16:31.658-0700][info][gc,load     ] GC(1) Load: 0.02/0.07/0.06
[2024-07-07T08:16:31.658-0700][info][gc,mmu      ] GC(1) MMU: 2ms/94.5%, 5ms/97.8%, 10ms/97.9%, 20ms/99.0%, 50ms/99.4%, 100ms/99.7%
[2024-07-07T08:16:31.658-0700][info][gc,marking  ] GC(1) Mark: 1 stripe(s), 2 proactive flush(es), 1 terminate flush(es), 0 completion(s), 0 continuation(s) 
[2024-07-07T08:16:31.658-0700][info][gc,marking  ] GC(1) Mark Stack Usage: 32M
[2024-07-07T08:16:31.658-0700][info][gc,nmethod  ] GC(1) NMethods: 119 registered, 0 unregistered
[2024-07-07T08:16:31.658-0700][info][gc,metaspace] GC(1) Metaspace: 0M used, 0M committed, 1088M reserved
[2024-07-07T08:16:31.658-0700][info][gc,ref      ] GC(1) Soft: 29 encountered, 0 discovered, 0 enqueued
[2024-07-07T08:16:31.658-0700][info][gc,ref      ] GC(1) Weak: 96 encountered, 0 discovered, 0 enqueued
[2024-07-07T08:16:31.658-0700][info][gc,ref      ] GC(1) Final: 0 encountered, 0 discovered, 0 enqueued
[2024-07-07T08:16:31.658-0700][info][gc,ref      ] GC(1) Phantom: 1 encountered, 0 discovered, 0 enqueued
[2024-07-07T08:16:31.658-0700][info][gc,reloc    ] GC(1) Small Pages: 23 / 46M, Empty: 0M, Relocated: 9M, In-Place: 0
[2024-07-07T08:16:31.658-0700][info][gc,reloc    ] GC(1) Medium Pages: 0 / 0M, Empty: 0M, Relocated: 0M, In-Place: 0
[2024-07-07T08:16:31.658-0700][info][gc,reloc    ] GC(1) Large Pages: 0 / 0M, Empty: 0M, Relocated: 0M, In-Place: 0
[2024-07-07T08:16:31.658-0700][info][gc,reloc    ] GC(1) Forwarding Usage: 0M
[2024-07-07T08:16:31.658-0700][info][gc,heap     ] GC(1) Min Capacity: 4096M(100%)
[2024-07-07T08:16:31.658-0700][info][gc,heap     ] GC(1) Max Capacity: 4096M(100%)
[2024-07-07T08:16:31.658-0700][info][gc,heap     ] GC(1) Soft Max Capacity: 4096M(100%)
[2024-07-07T08:16:31.659-0700][info][gc,heap     ] GC(1)                Mark Start          Mark End        Relocate Start      Relocate End           High               Low         
[2024-07-07T08:16:31.659-0700][info][gc,heap     ] GC(1)  Capacity:     4096M (100%)       4096M (100%)       4096M (100%)       4096M (100%)       4096M (100%)       4096M (100%)   
[2024-07-07T08:16:31.659-0700][info][gc,heap     ] GC(1)      Free:     4050M (99%)        4048M (99%)        4048M (99%)        4066M (99%)        4066M (99%)        4046M (99%)    
[2024-07-07T08:16:31.659-0700][info][gc,heap     ] GC(1)      Used:       46M (1%)           48M (1%)           48M (1%)           30M (1%)           50M (1%)           30M (1%)     
[2024-07-07T08:16:31.659-0700][info][gc,heap     ] GC(1)      Live:         -                25M (1%)           25M (1%)           25M (1%)             -                  -          
[2024-07-07T08:16:31.659-0700][info][gc,heap     ] GC(1) Allocated:         -                 2M (0%)            2M (0%)            0M (0%)             -                  -          
[2024-07-07T08:16:31.659-0700][info][gc,heap     ] GC(1)   Garbage:         -                20M (0%)           20M (0%)            3M (0%)             -                  -          
[2024-07-07T08:16:31.659-0700][info][gc,heap     ] GC(1) Reclaimed:         -                  -                 0M (0%)           16M (0%)             -                  -          
[2024-07-07T08:16:31.659-0700][info][gc          ] GC(1) Garbage Collection (Timer) 46M(1%)->30M(1%)
```

上面是堆大小为 4g，停顿时间为 100ms 的 ZGC 打印的日志。

- 首先还是 gc,init 阶段，这个阶段主要展示了本次程序的一些参数配置。
- 接下来是 GC(0) 和 GC(1) 这两个事件，ZGC的日志会打印出每个阶段所用的时间，这也是和 G1 的日志很不同的地方。对于 GC(0) 这个事件：
    - Pause Mark Start: 停顿标记开始阶段，耗时0.092ms。
    - Concurrent Mark: 并发标记阶段，耗时7.651ms。
    - Pause Mark End: 停顿标记结束阶段，耗时0.094ms。
    - Concurrent Mark Free: 并发标记释放阶段，耗时0.018ms。
    - Concurrent Process Non-Strong References: 并发处理非强引用阶段，耗时2.388ms。
    - Concurrent Reset Relocation Set: 并发重置重新定位集阶段，耗时0.017ms。
    - Concurrent Select Relocation Set: 并发选择重新定位集阶段，耗时47.741ms。
    - Pause Relocate Start: 停顿重新定位开始阶段，耗时0.096ms。
    - Concurrent Relocate: 并发重新定位阶段，耗时1.016ms。
    
    在打印这些阶段的耗时之后，日志说明了 GC 的负载和 MMU：
    
    - Load: 显示在不同时间窗口内的系统负载：1分钟负载0.02，5分钟负载0.07，15分钟负载0.06。
    - MMU: 显示最小暂停时间的利用率（Minimum Mutator Utilization）在不同时间窗口内的百分比：
        - 2ms窗口：95.2%
        - 5ms窗口：98.1%
        - 10ms窗口：98.1%
        - 20ms窗口：99.1%
        - 50ms窗口：99.6%
        - 100ms窗口：99.7%
    
    再接下来，日志打印了堆的使用情况：
    
    - Min Capacity, Max Capacity, Soft Max Capacity, Committed, Reserved: 堆的最小、最大、soft 最大、已提交和保留容量都是4096MB，表示堆的容量是固定的。
    - Used: 使用的堆内存为14MB。
    - Free: 空闲的堆内存为4082MB。
    - Live: 活动对象占用的内存为1MB。
    - Allocated: 已分配的内存为13MB。
    - Garbage: 垃圾对象占用的内存为0MB。

在分析一个 ZGC 的垃圾回收日志，我们会发现，ZGC 的日志会比 G1 日志，在时间和堆使用情况方面详细很多，这也主要是因为 ZGC 的设计目标是实现极低的暂停时间（通常低于10ms），并且能够处理大内存堆（数百GB甚至TB级别）。为了实现这些目标，ZGC 在日志中提供了非常详细的信息，以便开发者和运维人员能够监控和调优其性能。ZGC 的许多操作是并发进行的，而不是像G1那样在安全点上进行停顿。为了确保这些并发操作的正确性和高效性，ZGC 需要详细记录每个并发阶段的具体时间和任务，这样可以帮助开发者在需要时分析和调试并发操作的性能。

而我在日志中还发现，ZGC会打印出诸如下面这类的 statistics 信息：

```
[2024-07-07T08:18:20.321-0700][info][gc,stats    ] === Garbage Collection Statistics =======================================================================================================================
[2024-07-07T08:18:20.321-0700][info][gc,stats    ]                                                              Last 10s              Last 10m              Last 10h                Total
[2024-07-07T08:18:20.321-0700][info][gc,stats    ]                                                              Avg / Max             Avg / Max             Avg / Max             Avg / Max
[2024-07-07T08:18:20.321-0700][info][gc,stats    ]   Collector: Garbage Collection Cycle                   2549.122 / 3475.024   1032.225 / 3475.024   1032.225 / 3475.024   1032.225 / 3475.024    ms
[2024-07-07T08:18:20.321-0700][info][gc,stats    ]  Contention: Mark Segment Reset Contention                     0 / 0                 0 / 0                 0 / 0                 0 / 0           ops/s
[2024-07-07T08:18:20.321-0700][info][gc,stats    ]  Contention: Mark SeqNum Reset Contention                      0 / 0                 0 / 0                 0 / 0                 0 / 0           ops/s
[2024-07-07T08:18:20.321-0700][info][gc,stats    ]    Critical: Allocation Stall                                  0 / 0                 0 / 0                 0 / 0                 0 / 0           ops/s
[2024-07-07T08:18:20.321-0700][info][gc,stats    ]    Critical: Allocation Stall                              0.000 / 0.000         0.000 / 0.000         0.000 / 0.000         0.000 / 0.000       ms
[2024-07-07T08:18:20.321-0700][info][gc,stats    ]    Critical: GC Locker Stall                                   0 / 0                 0 / 0                 0 / 0                 0 / 0           ops/s
[2024-07-07T08:18:20.321-0700][info][gc,stats    ]    Critical: GC Locker Stall                               0.000 / 0.000         0.000 / 0.000         0.000 / 0.000         0.000 / 0.000       ms
[2024-07-07T08:18:20.321-0700][info][gc,stats    ]    Critical: Relocation Stall                                  0 / 0                 0 / 0                 0 / 0                 0 / 0           ops/s
[2024-07-07T08:18:20.321-0700][info][gc,stats    ]    Critical: Relocation Stall                              0.000 / 0.000         0.000 / 0.000         0.000 / 0.000         0.000 / 0.000       ms
[2024-07-07T08:18:20.321-0700][info][gc,stats    ]      Memory: Allocation Rate                                   6 / 12               16 / 190              16 / 190              16 / 190         MB/s
[2024-07-07T08:18:20.321-0700][info][gc,stats    ]      Memory: Out Of Memory                                     0 / 0                 0 / 0                 0 / 0                 0 / 0           ops/s
[2024-07-07T08:18:20.321-0700][info][gc,stats    ]      Memory: Page Cache Flush                                  0 / 0                 0 / 0                 0 / 0                 0 / 0           MB/s
[2024-07-07T08:18:20.321-0700][info][gc,stats    ]      Memory: Page Cache Hit L1                                 3 / 14                7 / 48                7 / 48                7 / 48          ops/s
[2024-07-07T08:18:20.322-0700][info][gc,stats    ]      Memory: Page Cache Hit L2                                 0 / 0                 0 / 0                 0 / 0                 0 / 0           ops/s
[2024-07-07T08:18:20.322-0700][info][gc,stats    ]      Memory: Page Cache Hit L3                                 1 / 4                 3 / 40                3 / 40                3 / 40          ops/s
[2024-07-07T08:18:20.322-0700][info][gc,stats    ]      Memory: Page Cache Miss                                   0 / 0                 0 / 1                 0 / 1                 0 / 1           ops/s
[2024-07-07T08:18:20.322-0700][info][gc,stats    ]      Memory: Uncommit                                          0 / 0                 0 / 0                 0 / 0                 0 / 0           MB/s
[2024-07-07T08:18:20.322-0700][info][gc,stats    ]      Memory: Undo Object Allocation Failed                     0 / 0                 0 / 0                 0 / 0                 0 / 0           ops/s
[2024-07-07T08:18:20.322-0700][info][gc,stats    ]      Memory: Undo Object Allocation Succeeded                  9 / 96              153 / 1532            153 / 1532            153 / 1532        ops/s
[2024-07-07T08:18:20.322-0700][info][gc,stats    ]      Memory: Undo Page Allocation                              0 / 0                 0 / 0                 0 / 0                 0 / 0           ops/s
[2024-07-07T08:18:20.322-0700][info][gc,stats    ]       Phase: Concurrent Mark                            1277.676 / 1845.831    504.932 / 1845.831    504.932 / 1845.831    504.932 / 1845.831    ms
[2024-07-07T08:18:20.322-0700][info][gc,stats    ]       Phase: Concurrent Mark Continue                      0.000 / 0.000         0.000 / 0.000         0.000 / 0.000         0.000 / 0.000       ms
[2024-07-07T08:18:20.322-0700][info][gc,stats    ]       Phase: Concurrent Mark Free                          0.025 / 0.032         0.026 / 0.177         0.026 / 0.177         0.026 / 0.177       ms
[2024-07-07T08:18:20.322-0700][info][gc,stats    ]       Phase: Concurrent Process Non-Strong References      2.325 / 2.525         4.800 / 132.070       4.800 / 132.070       4.800 / 132.070     ms
[2024-07-07T08:18:20.322-0700][info][gc,stats    ]       Phase: Concurrent Relocate                          16.610 / 21.151       12.769 / 31.604       12.769 / 31.604       12.769 / 31.604      ms
[2024-07-07T08:18:20.322-0700][info][gc,stats    ]       Phase: Concurrent Reset Relocation Set               0.023 / 0.024         0.027 / 0.273         0.027 / 0.273         0.027 / 0.273       ms
[2024-07-07T08:18:20.322-0700][info][gc,stats    ]       Phase: Concurrent Select Relocation Set              3.627 / 6.738         3.206 / 47.741        3.206 / 47.741        3.206 / 47.741      ms
[2024-07-07T08:18:20.322-0700][info][gc,stats    ]       Phase: Pause Mark End                                0.115 / 0.124         0.130 / 0.370         0.130 / 0.370         0.130 / 0.370       ms
[2024-07-07T08:18:20.322-0700][info][gc,stats    ]       Phase: Pause Mark Start                              0.119 / 0.122         0.133 / 0.394         0.133 / 0.394         0.133 / 0.394       ms
[2024-07-07T08:18:20.322-0700][info][gc,stats    ]       Phase: Pause Relocate Start                          0.109 / 0.118         0.124 / 0.268         0.124 / 0.268         0.124 / 0.268       ms
[2024-07-07T08:18:20.322-0700][info][gc,stats    ]    Subphase: Concurrent Classes Purge                      0.345 / 0.364         0.434 / 1.666         0.434 / 1.666         0.434 / 1.666       ms
[2024-07-07T08:18:20.322-0700][info][gc,stats    ]    Subphase: Concurrent Classes Unlink                     0.551 / 0.613         0.621 / 3.760         0.621 / 3.760         0.621 / 3.760       ms
[2024-07-07T08:18:20.322-0700][info][gc,stats    ]    Subphase: Concurrent Mark                            1276.540 / 1845.048    503.766 / 1845.048    503.766 / 1845.048    503.766 / 1845.048    ms
[2024-07-07T08:18:20.322-0700][info][gc,stats    ]    Subphase: Concurrent Mark Try Flush                   331.231 / 1775.788    139.859 / 1775.788    139.859 / 1775.788    139.859 / 1775.788    ms
[2024-07-07T08:18:20.322-0700][info][gc,stats    ]    Subphase: Concurrent Mark Try Terminate                 0.351 / 0.744        11.128 / 738.974      11.128 / 738.974      11.128 / 738.974     ms
[2024-07-07T08:18:20.323-0700][info][gc,stats    ]    Subphase: Concurrent References Enqueue                 0.021 / 0.022         0.024 / 0.078         0.024 / 0.078         0.024 / 0.078       ms
[2024-07-07T08:18:20.323-0700][info][gc,stats    ]    Subphase: Concurrent References Process                 0.289 / 0.330         0.387 / 3.367         0.387 / 3.367         0.387 / 3.367       ms
[2024-07-07T08:18:20.323-0700][info][gc,stats    ]    Subphase: Concurrent Roots ClassLoaderDataGraph         0.053 / 0.056         0.073 / 0.398         0.073 / 0.398         0.073 / 0.398       ms
[2024-07-07T08:18:20.323-0700][info][gc,stats    ]    Subphase: Concurrent Roots CodeCache                    0.000 / 0.000         0.000 / 0.000         0.000 / 0.000         0.000 / 0.000       ms
[2024-07-07T08:18:20.323-0700][info][gc,stats    ]    Subphase: Concurrent Roots JavaThreads                  0.125 / 0.136         0.153 / 0.455         0.153 / 0.455         0.153 / 0.455       ms
[2024-07-07T08:18:20.323-0700][info][gc,stats    ]    Subphase: Concurrent Roots OopStorageSet                0.034 / 0.045         0.039 / 0.186         0.039 / 0.186         0.039 / 0.186       ms
[2024-07-07T08:18:20.323-0700][info][gc,stats    ]    Subphase: Concurrent Weak Roots OopStorageSet           0.078 / 0.083         0.096 / 0.332         0.096 / 0.332         0.096 / 0.332       ms
[2024-07-07T08:18:20.323-0700][info][gc,stats    ]    Subphase: Pause Mark Try Complete                       0.000 / 0.000         0.067 / 0.067         0.067 / 0.067         0.067 / 0.067       ms
[2024-07-07T08:18:20.323-0700][info][gc,stats    ]      System: Java Threads                                     11 / 11               11 / 11               11 / 11               11 / 11          threads
[2024-07-07T08:18:20.323-0700][info][gc,stats    ] =========================================================================================================================================================
```

这段日志展示了ZGC在一段时间内的垃圾收集统计信息，涵盖了最后10秒、10分钟、10小时以及总计的情况。

### G1-4g-100ms-1m VS G1-4g-200ms-1m VS G1-4g-500ms-1m

本部分我们先来分析一下停顿时间对实验结果带来的影响。

|  | 吞吐量 | 总停顿时间 | Young GC 次数 | Young GC 平均时间 | Young GC 总时间 | Full GC 次数 | Full GC 平均时间 | Full GC 总时间 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 100ms | 98.704% | 25.6s | 180 | 1.035s | 3min6.289s | 29 | 0.467s | 13.53s |
| 200ms | 98.683% | 22.671s | 164 | 1.649s | 4min30.478s | 27 | 0.478s | 12.9s |
| 500ms | 97.956% | 25.352s | 195 | 1.595s | 5min10.99s | 30 | 0.354s | 10.61s |

首先可以很明显看出来的是：**停顿时间对吞吐量有着明显的改变。随着停顿时间的增加，吞吐量不断降低。** 主要原因在于较长的GC停顿时间会略微减少应用的有效工作时间。

然后一个明显看出来的是：**停顿时间对Young GC 的整体时间影响很大。随着停顿时间的增加，Young GC 的整体时间不断增加。** 主要原因在于对于 G1 来说，它会根据我们设置的停顿时间进行自动调优，而我感觉可以解释这个现象的产生。

### G1-4g-100ms-1m VS G1-4g-100ms-2m VS G1-4g-100ms-4m

本部分主要分析 region 的大小对实验结果的影响。

|  | 吞吐量 | 总停顿时间 | Young GC 次数 | Young GC 平均时间 | Young GC 总时间 | Full GC 次数 | Full GC 平均时间 | Full GC 总时间 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1m | 98.704% | 25.6s | 180 | 1.035s | 3min6.289s | 29 | 0.467s | 13.53s |
| 2m | 98.269% | 19.626s | 165 | 2.49s | 6min50.85s | 31 | 0.353s | 10.95s |
| 4m | 98.321% | 21.838s | 155 | 2.294s | 5min55.609s | 35 | 0.356s | 12.46s |

首先可以很明显看出来的是：**随着 Region 区域的增加，Young GC 的次数有了比较明显的下降，而每次 Young GC 的时间逐渐增加。** 这主要是因为较大的区域能够容纳更多的对象，从而减少了Young GC的触发频率。每个GC周期内有更多的对象需要处理，因此GC的触发频率降低。区域增大，GC需要处理更多的数据。尽管GC触发次数减少，但每次GC需要更多的时间来完成处理。

其次我们可以发现：**随着 Region 区域增加，Full GC 的次数不断增加，而每次 Full GC 的时间不断波动。** 这主要是因为较大的区域可能导致长时间未被回收的对象积累，从而导致更多的Full GC事件。对象的生命周期更长，可能导致内存的使用情况变得更加复杂，增加了Full GC的频率。Full GC的平均时间和总时间在不同区域大小下的波动，可能由于不同区域大小对Full GC的效率影响不同。中等大小的区域可能在处理Full GC时更高效，但更大的区域可能使Full GC变得更复杂，导致时间的增加。

### G1-4g-100ms-1m VS G1-8g-100ms-1m VS G1-16g-100ms-1m

本部分主要分析堆大小对实验结果的影响。

|  | 吞吐量 | 总停顿时间 | Young GC 次数 | Young GC 平均时间 | Young GC 总时间 | Full GC 次数 | Full GC 平均时间 | Full GC 总时间 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 4g | 98.704% | 25.6s | 180 | 1.035s | 3min6.289s | 29 | 0.467s | 13.53s |
| 8g | 99.408% | 18.891s | 30 | 0.741s | 22.230s | - | - | - |
| 16g | 99.87% | 9.111s | 14 | 0.651s | 9.11s | - | - | - |

首先我们可以发现：**随着堆大小的增加，Young GC 和 Full GC 的次数有了显著的减少，甚至对于 8g 和 16g 并没有触发 Full GC，而他们的时间也有了显著的减少。** 这主要是因为增大堆大小意味着有更多的内存用于分配新对象，新生代（Eden 区域和 Survivor 区域）的空间变大。因此，Young GC 触发的频率减少，因为 Eden 区域能够容纳更多的对象，减少了其满的频率。 更大的堆意味着每次 Young GC 回收的区域更大，但由于 GC 的频率减少，整体 Young GC 的时间也会减少。尽管单次 GC 的时间可能稍长，但总的 Young GC 时间下降。而对于 Full GC，较大的堆大小减缓了老年代的空间消耗速度，因此 Full GC 的触发频率减少。在更大的堆中，老年代的数据被回收的速度较慢，因此需要进行 Full GC 的次数减少。

其次我们也可以很明显的看出来：**随着堆大小的增长，吞吐量也有了显著的增加。** 这主要是因为较大的堆大小减少了GC的频率和时间，使得应用程序的停顿时间减少，能够处理更多的业务逻辑，从而提高了吞吐量。堆大小的增加通常意味着在应用程序中花费更多时间而不是在 GC 上，从而提升吞吐量。

### ZGC-4g-100ms-4g VS ZGC-4g-200ms-4g VS ZGC-4g-500ms-4g

本部分主要分析停顿时间对实验结果的影响。

因为只有 Pause Mark Start、Pause Mark End 、Pause Relocate Start 这三个阶段可能会带来 STW，所以下面主要对它们进行分析。

|  | 吞吐量 | 总停顿时间 | Pause Mark Start 次数 | Pause Mark Start 总时间 | Pause Mark End 次数 | Pause Mark End 总时间 | Pause Relocate Start 次数 | Pause Relocate Start 总时间 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 100ms | 99.995% | 207ms | 526 | 0.0721s | 526 | 0.0693s | 526 | 0.0653s |
| 200ms | 99.995% | 232ms | 505 | 0.0768s | 505 | 0.0789 | 505 | 0.0758s |
| 500ms | 99.995% | 186ms | 489 | 0.0643s | 489 | 0.0627s | 489 | 0.0593s |

首先我们可以发现，吞吐量以及到达了极限，几乎没有很明显的变化，这说明 ZGC 在吞吐量方面的优化做的确实非常好。

接下来可以发现，停顿时间对 STW 的影响也并不大，对于这三个阶段，无论从总时间、总次数还是平均时间方面分析，变化都不大。

### ZGC-4g-100ms-4g VS ZGC-8g-100ms-8g VS ZGC-16g-100ms-16g

本部分主要分析堆大小对实验结果的影响。

|  | 吞吐量 | 总停顿时间 | Pause Mark Start 次数 | Pause Mark Start 总时间 | Pause Mark Start 平均时间 | Pause Mark End 次数 | Pause Mark End 总时间 | Pause Mark End 平均时间 | Pause Relocate Start 次数 | Pause Relocate Start 总时间 | Pause Relocate Start 平均时间 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 4g | 99.995% | 207ms | 526 | 0.0721s | 0.137ms | 526 | 0.0693s | 0.132ms | 526 | 0.0653s | 0.124ms |
| 8g | 99.996% | 341ms | 872 | 0.114s | 0.131ms | 872 | 0.114s | 0.131ms | 872 | 0.113s | 0.130ms |
| 16g | 99.995% | 694ms | 23544 | 0.198s | 0.00843ms | 23544 | 0.302s | 0.01287ms | 23544 | 0.193s | 0.00822ms |

我们可以明显看出来：**随着堆大小不断增加，STW的次数有了显著的增加，STW 总时间整体增加，每次的 STW 的平均时间也显著减少。** 这主要是因为较大的堆空间可能需要更多的时间来进行垃圾回收操作，导致停顿时间增长。这可能是因为更大的堆需要处理更多的对象，垃圾回收的复杂性和所需时间也增加。每次 `Pause Mark Start` 的平均时间在4g和8g 堆之间变化不大，但在16g 堆下显著减少。这可能是由于更大的堆可能导致标记阶段的优化效果更明显。同理也可以分析 `Pause Mark End` 和 `Pause Relocate Start` 阶段。

### G1-4g-100-1m VS ZGC-4g-100

|  | G1 | ZGC |
| --- | --- | --- |
| 吞吐量 | 98.704% | 99.995% |
| 停顿时间 | 25.6s | 0.207s |

### G1-16g-100-1m VS ZGC-16g-100

|  | G1 | ZGC |
| --- | --- | --- |
| 吞吐量 | 99.87% | 99.995% |
| 停顿时间 | 9.111s | 0.694s |

我们可以看到，对于堆大小为 GB 级别的程序来说，ZGC 对 G1 无论是吞吐量还是停顿时间方面都是降维打击，提升都非常显著。

## 结果分析

通过上面的实验，我们可以得到下面的结论：

选择 G1 的场景：

- 中等到大规模堆内存：适用于数百MB到数十GB的堆内存。
- 较高的吞吐量需求：适合需要较高吞吐量的应用。
- 可接受的停顿时间：适用于对停顿时间要求不特别严格的场景。

选择 ZGC 的场景：

- 超大规模堆内存：适用于TB级别的堆内存。
- 极低的停顿时间需求：适合需要极低停顿时间的应用，如实时系统。
- 较高的响应速度需求：适用于需要高响应速度和低延迟的场景。

## 附加思考

### 内存开销方面，G1 VS ZGC

### 支持不同大小堆，G1 VS ZGC