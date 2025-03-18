package com.jianhui.project.frameworks.starter.designpattern.chain;

import com.jianhui.project.framework.starter.bases.ApplicationContextHolder;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;


/**
 * 责任链上下文
 * TODO:OrderCreateFilter
 */
public final class AbstractChainContext<T> implements CommandLineRunner {

    private Map<String, List<AbstractChainHandler>> abstrachChainHandlerContainer = new HashMap<>();

    /**
     * 责任链组件执行
     *
     * @param mark         责任链组件标识
     * @param requestParam 请求参数
     */
    public void handler(String mark, T requestParam) {
        List<AbstractChainHandler> abstractChainHandlers = abstrachChainHandlerContainer.get(mark);
        if (abstractChainHandlers.isEmpty()) {
            throw new RuntimeException(String.format("[%s] Chain of Responsibility ID is undefined.", mark));
        }
        abstractChainHandlers.forEach(each -> each.handler(requestParam));
    }

    @Override
    public void run(String... args) throws Exception {
//        获得所有的责任链组件
        Map<String, AbstractChainHandler> filterMap = ApplicationContextHolder
                .getBeansOfType(AbstractChainHandler.class);
//        根据mark加入到map
        filterMap.forEach((beanName, bean) -> {
            List<AbstractChainHandler> abstractChainHandlers = abstrachChainHandlerContainer.get(bean.mark());
            if (CollectionUtils.isEmpty(abstractChainHandlers)) {
                abstractChainHandlers = new ArrayList<>();
            }
            abstractChainHandlers.add(bean);
            List<AbstractChainHandler> actualAbstractFilterChain = abstractChainHandlers.stream()
                    .sorted(Comparator.comparing(Ordered::getOrder))
                    .collect(Collectors.toList());
            abstrachChainHandlerContainer.put(bean.mark(), actualAbstractFilterChain);
        });
    }
}
