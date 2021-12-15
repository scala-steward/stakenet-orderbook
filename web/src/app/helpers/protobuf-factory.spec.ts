import { ProtobufFactory, newOrderDetails, newOrder } from 'src/app/helpers/protobuf-factory';

describe('ProtobufFactory', () => {


    it('should create a command', () => {
        const command = ProtobufFactory.createCancelOpenOrder('2');
        expect(command).toBeTruthy();
    });

    it('should serialize a command', () => {
        const command = ProtobufFactory.createGetHistoricTrades('XSN_BTC');
        const serializedCommand = command.serializeBinary();
        expect(serializedCommand).toBeTruthy();
    });

    it('should create a command object', () => {
        const command = ProtobufFactory.createSubscribe('XSN_BTC');
        const objectCommand = command.toObject();

        expect(objectCommand).toBeTruthy();
    });

    it('should create a command object', () => {
        const command = ProtobufFactory.createSubscribe('XSN_BTC');
        const objectCommand = command.toObject();

        expect(objectCommand).toBeTruthy();
    });


    it('should create a new Order details', () => {
        const orderDetails = newOrderDetails('500', '600', 'asdf');
        expect(orderDetails).toBeTruthy();


        const detailsObject = orderDetails.toObject();
        expect(detailsObject.funds.value).toEqual('500');
        expect(detailsObject.price.value).toEqual('600');
        expect(detailsObject.orderid).toEqual('asdf');
    });


    it('should create a new Order', () => {
        const orderDetails = newOrderDetails('500', '600', 'asdf');
        expect(orderDetails).toBeTruthy();

        const order = newOrder('XSN_BTC', 0, 1, orderDetails);
        expect(order).toBeTruthy();

        const orderObject = order.toObject();
        expect(orderObject.tradingpair).toEqual('XSN_BTC');
        expect(orderObject.type).toEqual(0);
        expect(orderObject.side).toEqual(1);
        expect(orderObject.details).toEqual(orderDetails.toObject());

    });

});
